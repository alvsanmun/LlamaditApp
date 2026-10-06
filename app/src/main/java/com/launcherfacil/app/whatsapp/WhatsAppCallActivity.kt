package com.launcherfacil.app.whatsapp

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.launcherfacil.app.R
import com.launcherfacil.app.call.setupCallWindow
import com.launcherfacil.app.contacts.findFavoriteByName
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.ui.CallButton
import com.launcherfacil.app.ui.CallLayout
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.VerdeLlamar
import com.launcherfacil.app.volume.VolumeController
import com.launcherfacil.app.volume.WithVolumeOverlay

/**
 * Llamada entrante de WhatsApp con nuestra pantalla. Al contestar, WhatsApp abre
 * su propia pantalla de videollamada (con su botón rojo para colgar).
 */
class WhatsAppCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setupCallWindow()
        super.onCreate(savedInstanceState)
        setContent { WithVolumeOverlay { WhatsAppCallRoute(onFinished = ::finish) } }
    }

    // Botones de volumen: solo el volumen del teléfono, con el panel grande.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        VolumeController.onKeyEvent(this, event) || super.dispatchKeyEvent(event)
}

@Composable
private fun WhatsAppCallRoute(onFinished: () -> Unit) {
    val context = LocalContext.current
    val call by WhatsAppIncoming.call.collectAsState()

    BackHandler {}
    LaunchedEffect(call == null) { if (call == null) onFinished() }

    val current = call ?: return
    // Mejor la foto que puso el cuidador en la agenda; si no, la de perfil de WhatsApp.
    val photo by produceState<ImageBitmap?>(current.notificationPhoto?.asImageBitmap(), current.key) {
        findFavoriteByName(context, current.name)
            ?.let { loadContactPhoto(context, it.id) }
            ?.let { value = it }
    }

    CallLayout(
        photo = photo,
        caption = current.name,
        status = null,
        isVideo = current.isVideo
    ) {
        if (current.decline != null) {
            CallButton(RojoSos, R.drawable.ic_call_end, { WhatsAppIncoming.decline(context) })
        }
        CallButton(
            VerdeLlamar,
            if (current.isVideo) R.drawable.ic_videocam else R.drawable.ic_call,
            { WhatsAppIncoming.answer(context) },
            pulse = true
        )
    }
}
