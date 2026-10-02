package com.launcherfacil.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.whatsAppVideoCall
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.sos.SosService
import com.launcherfacil.app.ui.ContactsScreen
import com.launcherfacil.app.ui.DialpadScreen
import com.launcherfacil.app.ui.HomeScreen
import com.launcherfacil.app.ui.WhatsAppScreen
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
        setContent { LauncherApp(debugSosInterval, homeRequests) }
    }

    override fun onResume() {
        super.onResume()
        Kiosk.enterLockTask(this)
    }

    // Pulsar Inicio (fuera del quiosco) con la app abierta: volver a la pantalla principal.
    private var homeRequests by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homeRequests++
    }
}

private enum class Screen { Home, Contacts, WhatsApp, Dialpad }

private val PERMISSIONS = APP_PERMISSIONS.toTypedArray()

@Composable
private fun LauncherApp(debugSosInterval: Int = 0, homeRequests: Int = 0) {
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

    // SOS: llamada inmediata al contacto de confianza (por ahora, el primer favorito)
    // y, en paralelo, el servicio envía los SMS con la ubicación.
    val scope = rememberCoroutineScope()
    fun sos() {
        context.startService(
            Intent(context, SosService::class.java)
                .putExtra(SosService.EXTRA_DEBUG_INTERVAL_SECONDS, debugSosInterval)
        )
        scope.launch {
            val contact = if (canReadContacts) loadFavoriteContacts(context).firstOrNull() else null
            if (contact != null && canCall) context.placeCall(contact.phone) else context.dialEmergency()
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
    LaunchedEffect(homeRequests) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            onCall = { screen = Screen.Contacts },
            onWhatsApp = { screen = Screen.WhatsApp },
            onDialpad = { screen = Screen.Dialpad },
            onSos = ::sos
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
            onHome = { screen = Screen.Home }
        )
    }
}

private fun whatsAppFailed(context: Context) =
    Toast.makeText(context, "No se pudo abrir WhatsApp", Toast.LENGTH_LONG).show()
