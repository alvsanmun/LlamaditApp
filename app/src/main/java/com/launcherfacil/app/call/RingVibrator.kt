package com.launcherfacil.app.call

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Las llamadas entrantes siempre vibran, también con el volumen al máximo o en
 * silencio, para que la persona las note si lleva el móvil encima.
 */
object RingVibrator {
    // Vibra 0,8 s, para 0,6 s, y repite mientras suena.
    private val PATTERN = longArrayOf(0, 800, 600)
    private var vibrating = false

    fun update(context: Context, ringing: Boolean) {
        if (ringing == vibrating) return
        vibrating = ringing
        val vibrator = vibrator(context) ?: return
        if (!ringing) {
            vibrator.cancel()
            return
        }
        val effect = VibrationEffect.createWaveform(PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_RINGTONE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
            )
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
