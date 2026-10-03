package com.launcherfacil.app.call

import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Lo que muestra la pantalla de llamada. */
data class CallInfo(
    val state: Int,
    val number: String,
    /** Momento en que se conectó (0 si aún no), para el contador de duración. */
    val connectTimeMillis: Long
)

/**
 * Llamadas en curso que nos entrega Android (somos la app de Teléfono). Las
 * pantallas observan [call] y [speakerOn]; los botones llaman a [answer], etc.
 */
object CallManager {
    private val calls = mutableListOf<Call>()
    private var service: InCallService? = null

    private val _call = MutableStateFlow<CallInfo?>(null)
    val call: StateFlow<CallInfo?> = _call

    private val _speakerOn = MutableStateFlow(false)
    val speakerOn: StateFlow<Boolean> = _speakerOn

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    fun attach(service: InCallService) {
        this.service = service
    }

    fun detach(service: InCallService) {
        if (this.service === service) this.service = null
    }

    fun add(call: Call) {
        calls += call
        call.registerCallback(callback)
        publish()
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls -= call
        publish()
    }

    /** La llamada que se muestra: una entrante tiene prioridad (llamada en espera). */
    private fun current(): Call? =
        calls.firstOrNull { it.stateCompat() == Call.STATE_RINGING }
            ?: calls.lastOrNull { it.stateCompat() != Call.STATE_DISCONNECTED }
            ?: calls.lastOrNull()

    private fun publish() {
        _call.value = current()?.let {
            CallInfo(
                state = it.stateCompat(),
                number = it.details.handle?.schemeSpecificPart.orEmpty(),
                connectTimeMillis = it.details.connectTimeMillis
            )
        }
    }

    fun answer() = current()?.answer(VideoProfile.STATE_AUDIO_ONLY)

    fun reject() = current()?.reject(false, null)

    fun hangUp() = current()?.disconnect()

    fun setSpeaker(on: Boolean) {
        @Suppress("DEPRECATION")
        service?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_WIRED_OR_EARPIECE
        )
    }

    fun onAudioStateChanged(state: CallAudioState?) {
        _speakerOn.value = state?.route == CallAudioState.ROUTE_SPEAKER
    }
}

@Suppress("DEPRECATION")
private fun Call.stateCompat(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) details.state else state
