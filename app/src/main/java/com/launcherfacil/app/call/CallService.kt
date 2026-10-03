package com.launcherfacil.app.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * Android nos entrega aquí todas las llamadas normales (entrantes y salientes)
 * porque somos la app de Teléfono predeterminada. Mostramos nuestra pantalla.
 * El tono de llamada lo sigue poniendo el sistema.
 */
class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.attach(this)
        CallManager.add(call)
        try {
            startActivity(
                Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e("CallService", "No se pudo abrir la pantalla de llamada", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    @Deprecated("Sustituido por onCallEndpointChanged en Android 14, pero sigue llamándose")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.onAudioStateChanged(audioState)
    }

    override fun onDestroy() {
        CallManager.detach(this)
        super.onDestroy()
    }
}
