package com.launcherfacil.app.call

import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.findContactByNumber
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.ui.AzulTeclado
import com.launcherfacil.app.ui.CallButton
import com.launcherfacil.app.ui.CallLayout
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.VerdeLlamar
import com.launcherfacil.app.ui.formatDuration
import kotlinx.coroutines.delay

/** Pantalla de llamada normal: entrante (descolgar/colgar) y en curso (colgar/altavoz). */
class CallActivity : ComponentActivity() {

    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setupCallWindow()
        super.onCreate(savedInstanceState)
        setContent { CallRoute(onFinished = ::finish, onProximity = ::setProximity) }
    }

    /** Apaga la pantalla al acercar el móvil a la oreja (para no colgar con la mejilla). */
    private fun setProximity(enabled: Boolean) {
        val pm = getSystemService(PowerManager::class.java)
        if (enabled && proximityLock == null &&
            pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)
        ) {
            proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "launcherfacil:call")
                .also { it.acquire(3 * 60 * 60 * 1000L) }
        } else if (!enabled) {
            proximityLock?.takeIf { it.isHeld }?.release()
            proximityLock = null
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

@Composable
private fun CallRoute(onFinished: () -> Unit, onProximity: (Boolean) -> Unit) {
    val context = LocalContext.current
    val info by CallManager.call.collectAsState()
    val speakerOn by CallManager.speakerOn.collectAsState()

    // Durante la llamada, Atrás no hace nada (no queremos salir sin colgar).
    BackHandler {}

    val current = info
    val state = current?.state ?: Call.STATE_DISCONNECTED
    LaunchedEffect(state) {
        if (state == Call.STATE_DISCONNECTED) {
            delay(800)
            onFinished()
        }
    }
    LaunchedEffect(state, speakerOn) {
        onProximity(state == Call.STATE_ACTIVE && !speakerOn)
    }

    val number = current?.number.orEmpty()
    val contact by produceState<Contact?>(null, number) {
        value = if (number.isNotEmpty()) findContactByNumber(context, number) else null
    }
    val photo by produceState<ImageBitmap?>(null, contact?.id) {
        value = contact?.let { loadContactPhoto(context, it.id) }
    }

    // Contador de duración una vez conectada.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (state == Call.STATE_ACTIVE) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val connectedAt = current?.connectTimeMillis ?: 0
    val status = if (state == Call.STATE_ACTIVE && connectedAt > 0) {
        formatDuration(((now - connectedAt) / 1000).coerceAtLeast(0))
    } else null

    CallLayout(
        photo = photo,
        caption = contact?.name ?: number,
        status = status,
        isVideo = false
    ) {
        when (state) {
            Call.STATE_RINGING -> {
                CallButton(RojoSos, R.drawable.ic_call_end, CallManager::reject)
                CallButton(VerdeLlamar, R.drawable.ic_call, CallManager::answer, pulse = true)
            }
            Call.STATE_DISCONNECTED -> Unit
            else -> {
                CallButton(
                    if (speakerOn) AzulTeclado else GrisBoton,
                    R.drawable.ic_volume_up,
                    { CallManager.setSpeaker(!speakerOn) }
                )
                CallButton(RojoSos, R.drawable.ic_call_end, CallManager::hangUp, weight = 2f)
            }
        }
    }
}
