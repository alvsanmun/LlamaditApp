package com.launcherfacil.app.volume

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Último cambio de volumen, para el panel grande. [id] cambia en cada pulsación. */
data class VolumeChange(val level: Int, val max: Int, val up: Boolean, val id: Long)

/**
 * Botones de volumen: solo cambian el volumen del teléfono (el tono de llamada o,
 * durante una llamada, la voz de quien habla). El tono se puede silenciar del todo:
 * entonces el móvil queda en "solo vibración" (las llamadas siempre vibran, ver
 * [com.launcherfacil.app.call.RingVibrator]). Vídeos y música quedan a cero.
 */
object VolumeController {
    private const val TAG = "Volume"

    private val _change = MutableStateFlow<VolumeChange?>(null)
    val change: StateFlow<VolumeChange?> = _change

    private var tone: ToneGenerator? = null

    /**
     * Llamar desde dispatchKeyEvent. Devuelve true si era un botón de volumen (y así
     * no aparece el panel de volumen del sistema).
     */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val up = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> true
            KeyEvent.KEYCODE_VOLUME_DOWN -> false
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) adjust(context, up)
        return true
    }

    private fun adjust(context: Context, up: Boolean) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val inCall = am.mode == AudioManager.MODE_IN_CALL ||
            am.mode == AudioManager.MODE_IN_COMMUNICATION
        val stream = if (inCall) AudioManager.STREAM_VOICE_CALL else AudioManager.STREAM_RING
        val max = am.getStreamMaxVolume(stream)
        // Durante una llamada no se deja a 0: no se oiría a la otra persona.
        val min = if (inCall) 1 else 0
        val level = (am.getStreamVolume(stream) + if (up) 1 else -1).coerceIn(min, max)
        try {
            am.setStreamVolume(stream, level, 0) // 0 = sin el panel del sistema
            if (!inCall) setRingerModeFor(am, level)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo cambiar el volumen (¿No molestar?)", e)
        }
        // Un pitido corto al nuevo volumen para oír cómo sonará (no en llamada, ni mientras
        // suena una llamada, ni en silencio).
        val ringing = am.mode == AudioManager.MODE_RINGTONE
        if (!inCall && !ringing && level > 0) beep()
        _change.value = VolumeChange(level, max, up, System.nanoTime())
    }

    private fun beep() {
        try {
            val generator = tone ?: ToneGenerator(AudioManager.STREAM_RING, ToneGenerator.MAX_VOLUME)
                .also { tone = it }
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (e: RuntimeException) {
            Log.w(TAG, "No se pudo reproducir el pitido", e)
        }
    }

    /** Tono a 0 → "solo vibración" (nunca silencio total); si no, modo normal. */
    private fun setRingerModeFor(am: AudioManager, ringLevel: Int) {
        val mode = if (ringLevel == 0) AudioManager.RINGER_MODE_VIBRATE else AudioManager.RINGER_MODE_NORMAL
        if (am.ringerMode != mode) am.ringerMode = mode
    }

    /**
     * Estado inicial de los sonidos: modo de timbre acorde al volumen (nunca silencio
     * total, como mucho vibración) y vídeos y música a cero. Se llama al arrancar la app.
     */
    fun applySoundPolicy(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        try {
            setRingerModeFor(am, am.getStreamVolume(AudioManager.STREAM_RING))
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo aplicar la política de sonido", e)
        }
    }
}
