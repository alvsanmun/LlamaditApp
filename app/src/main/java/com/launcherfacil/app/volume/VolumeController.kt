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
 * durante una llamada, la voz de quien habla). Nunca baja hasta silencio, para no
 * perder llamadas. Los demás sonidos (vídeos, música) quedan a cero.
 */
object VolumeController {
    private const val TAG = "Volume"

    /** El tono de llamada nunca baja de aquí (1 = el nivel más bajo audible). */
    private const val MIN_RING_LEVEL = 1

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
        val min = if (inCall) 1 else MIN_RING_LEVEL
        val level = (am.getStreamVolume(stream) + if (up) 1 else -1).coerceIn(min, max)
        try {
            if (!inCall && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            am.setStreamVolume(stream, level, 0) // 0 = sin el panel del sistema
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo cambiar el volumen (¿No molestar?)", e)
        }
        // Un pitido corto al nuevo volumen para oír cómo sonará (no durante una llamada).
        if (!inCall) beep()
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

    /**
     * Estado inicial de los sonidos: timbre normal y audible, vídeos y música a cero.
     * Se llama al arrancar la app.
     */
    fun applySoundPolicy(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        try {
            if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
                am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
            if (am.getStreamVolume(AudioManager.STREAM_RING) < MIN_RING_LEVEL) {
                am.setStreamVolume(AudioManager.STREAM_RING, MIN_RING_LEVEL, 0)
            }
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo aplicar la política de sonido", e)
        }
    }
}
