/*
 * LlamaditApp
 * Copyright (C) 2026 alvsanmun
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.launcherfacil.app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.provider.CallLog
import android.provider.Telephony
import android.provider.Settings
import android.widget.Toast
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.resolveSosContact
import com.launcherfacil.app.contacts.whatsAppVideoCall
import com.launcherfacil.app.inbox.countNewMissedCalls
import com.launcherfacil.app.inbox.countNewSms
import com.launcherfacil.app.inbox.rememberContentVersion
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.sos.SosService
import com.launcherfacil.app.ui.ContactsScreen
import com.launcherfacil.app.ui.DialpadScreen
import com.launcherfacil.app.ui.HomeScreen
import com.launcherfacil.app.ui.MessagesScreen
import com.launcherfacil.app.ui.MissedCallsScreen
import com.launcherfacil.app.ui.PinScreen
import com.launcherfacil.app.ui.SettingsScreen
import com.launcherfacil.app.ui.WhatsAppScreen
import com.launcherfacil.app.volume.VolumeController
import com.launcherfacil.app.volume.WithVolumeOverlay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Fondo oscuro: iconos claros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        // Si es Device Owner: pantalla de inicio fija, apps permitidas y permisos.
        // Va antes de la interfaz para que los permisos ya estén concedidos.
        Kiosk.configure(this)
        // Solo para pruebas: `am start ... --ei debug_interval_seconds 15` acorta el
        // intervalo del SOS (el servicio lo ignora si la app no es depurable).
        val debugSosInterval = intent.getIntExtra(SosService.EXTRA_DEBUG_INTERVAL_SECONDS, 0)
        handleDial(intent)
        VolumeController.applySoundPolicy(this)
        setContent { WithVolumeOverlay { LauncherApp(debugSosInterval, homeRequests, dialRequest) } }
    }

    // Botones de volumen: solo el volumen del teléfono, con el panel grande.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        VolumeController.onKeyEvent(this, event) || super.dispatchKeyEvent(event)

    override fun onResume() {
        super.onResume()
        Kiosk.enterLockTask(this)
    }

    // Pulsar Inicio (fuera del quiosco) con la app abierta: volver a la pantalla principal.
    private var homeRequests by mutableIntStateOf(0)

    // Otra app (o el SOS sin contactos, con el 112) pide "marcar": abrir el teclado con
    // el número escrito. Se usa un objeto nuevo cada vez para que Compose lo note.
    private var dialRequest by mutableStateOf<DialRequest?>(null)

    private fun handleDial(intent: Intent) {
        if (intent.action != Intent.ACTION_DIAL) return
        val number = intent.data?.schemeSpecificPart.orEmpty().filter { it.isDigit() || it == '+' }
        dialRequest = DialRequest(number)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests++
        handleDial(intent)
        // Por si la app acaba de ser nombrada Device Owner estando ya abierta.
        Kiosk.enterLockTask(this)
    }
}

private class DialRequest(val number: String)

private enum class Screen { Home, Contacts, WhatsApp, Dialpad, Messages, MissedCalls, Pin, NewPin, Settings }

private val PERMISSIONS = APP_PERMISSIONS.toTypedArray()

@Composable
private fun LauncherApp(
    debugSosInterval: Int = 0,
    homeRequests: Int = 0,
    dialRequest: DialRequest? = null
) {
    val context = LocalContext.current
    fun granted(p: String) =
        ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    var canReadContacts by remember { mutableStateOf(granted(Manifest.permission.READ_CONTACTS)) }
    var canCall by remember { mutableStateOf(granted(Manifest.permission.CALL_PHONE)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        canReadContacts = granted(Manifest.permission.READ_CONTACTS)
        canCall = granted(Manifest.permission.CALL_PHONE)
    }
    // Se piden al arrancar. Como Device Owner ya están concedidos y no se pregunta nada.
    LaunchedEffect(Unit) {
        if (PERMISSIONS.any { !granted(it) }) permissionLauncher.launch(PERMISSIONS)
    }

    var screen by rememberSaveable { mutableStateOf(Screen.Home) }

    // Al terminar una llamada o videollamada se vuelve siempre a la pantalla principal.
    fun call(number: String) {
        if (!canCall) {
            permissionLauncher.launch(PERMISSIONS)
            return
        }
        context.placeCall(number)
        screen = Screen.Home
    }

    // SOS: llamada inmediata al contacto de confianza (el elegido por el cuidador o,
    // si no hay, el primer favorito) y, en paralelo, el servicio envía los SMS.
    val scope = rememberCoroutineScope()
    fun sos() {
        context.startService(
            Intent(context, SosService::class.java)
                .putExtra(SosService.EXTRA_DEBUG_INTERVAL_SECONDS, debugSosInterval)
        )
        scope.launch {
            val contact = if (canReadContacts) resolveSosContact(context) else null
            if (contact != null && canCall) context.placeCall(contact.phone) else context.dialEmergency()
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    LaunchedEffect(homeRequests) { screen = Screen.Home }
    LaunchedEffect(dialRequest) { if (dialRequest != null) screen = Screen.Dialpad }

    // Configuración del cuidador: se cierra al apagar la pantalla.
    val settings = remember { CaregiverSettings(context) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                if (screen in setOf(Screen.Pin, Screen.NewPin, Screen.Settings)) screen = Screen.Home
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val activity = context as Activity
    var kioskPaused by remember { mutableStateOf(Kiosk.isPaused(context)) }

    // Números de los botones de mensajes y llamadas perdidas: se recalculan al llegar
    // un SMS o una llamada y al volver a la pantalla principal.
    val inboxVersion = rememberContentVersion(Telephony.Sms.CONTENT_URI, CallLog.Calls.CONTENT_URI)
    val newSms by produceState(0, inboxVersion, screen) { value = countNewSms(context) }
    val newMissedCalls by produceState(0, inboxVersion, screen) { value = countNewMissedCalls(context) }

    when (screen) {
        Screen.Home -> HomeScreen(
            onCall = { screen = Screen.Contacts },
            onWhatsApp = { screen = Screen.WhatsApp },
            onDialpad = { screen = Screen.Dialpad },
            onSos = ::sos,
            onCaregiver = { screen = if (settings.hasPin) Screen.Pin else Screen.NewPin },
            onMessages = { screen = Screen.Messages },
            onMissedCalls = { screen = Screen.MissedCalls },
            newSms = newSms,
            newMissedCalls = newMissedCalls
        )
        Screen.Messages -> MessagesScreen(onHome = { screen = Screen.Home })
        Screen.MissedCalls -> MissedCallsScreen(
            onCallBack = ::call,
            onHome = { screen = Screen.Home }
        )
        Screen.Contacts -> ContactsScreen(
            hasPermission = canReadContacts,
            onRequestPermission = { permissionLauncher.launch(PERMISSIONS) },
            onContactClick = { call(it.phone) },
            onHome = { screen = Screen.Home }
        )
        Screen.WhatsApp -> WhatsAppScreen(
            hasPermission = canReadContacts,
            onRequestPermission = { permissionLauncher.launch(PERMISSIONS) },
            onVideoCall = {
                if (context.whatsAppVideoCall(it)) screen = Screen.Home else whatsAppFailed(context)
            },
            onHome = { screen = Screen.Home }
        )
        Screen.Dialpad -> DialpadScreen(
            onCall = ::call,
            onHome = { screen = Screen.Home },
            initialNumber = dialRequest?.number.orEmpty()
        )
        Screen.Pin, Screen.NewPin -> key(screen) {
            PinScreen(
                creating = screen == Screen.NewPin,
                onCheck = settings::checkPin,
                onSuccess = { screen = Screen.Settings },
                onCreated = {
                    settings.setPin(it)
                    screen = Screen.Settings
                },
                onHome = { screen = Screen.Home }
            )
        }
        Screen.Settings -> SettingsScreen(
            isDeviceOwner = Kiosk.isDeviceOwner(context),
            onChangePin = { screen = Screen.NewPin },
            onOpenContacts = {
                val contacts = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CONTACTS)
                if (!Kiosk.pauseAndOpen(activity, contacts)) openFailed(context)
            },
            onOpenAndroidSettings = {
                if (!Kiosk.pauseAndOpen(activity, Intent(Settings.ACTION_SETTINGS))) openFailed(context)
            },
            whatsAppAccess = NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName),
            onOpenNotificationAccess = {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                if (!Kiosk.pauseAndOpen(activity, intent)) openFailed(context)
            },
            kioskPaused = kioskPaused,
            onPauseKiosk = {
                Kiosk.pause(activity)
                kioskPaused = true
            },
            onResumeKiosk = {
                Kiosk.resume(activity)
                kioskPaused = false
                screen = Screen.Home
            },
            onDisableKiosk = {
                Kiosk.disable(context)
                screen = Screen.Home
            },
            onHome = { screen = Screen.Home }
        )
    }
}

private fun whatsAppFailed(context: Context) =
    Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()

private fun openFailed(context: Context) =
    Toast.makeText(context, "No se pudo abrir", Toast.LENGTH_LONG).show()
