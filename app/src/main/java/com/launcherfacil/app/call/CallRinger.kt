package com.launcherfacil.app.call

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * Tono de las llamadas entrantes, puesto por nuestra app y no por el sistema
 * (`IN_CALL_SERVICE_RINGING` en el manifiesto). Así, si se pulsan los botones de
 * volumen mientras suena, Android nos pide silenciarlo ([CallService.onSilenceRinger])
 * y no lo hacemos: la llamada sigue sonando hasta que se coge o se rechaza.
 * Usa el tono de llamada del móvil y su volumen (el de los botones de volumen).
 */
object CallRinger {
    private const val TAG = "CallRinger"
    private var ringtone: Ringtone? = null

    fun update(context: Context, ringing: Boolean) {
        if (ringing) start(context) else stop()
    }

    private fun start(context: Context) {
        if (ringtone?.isPlaying == true) return
        val am = context.getSystemService(AudioManager::class.java)
        // Con el volumen a 0 (modo vibración) solo vibra (RingVibrator).
        if (am != null && (am.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
                am.getStreamVolume(AudioManager.STREAM_RING) == 0)
        ) return
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(context, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
                play()
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reproducir el tono de llamada", e)
        }
    }

    private fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}
