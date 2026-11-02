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

package com.launcherfacil.app.remote

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * "Hacer sonar el móvil" desde la web del cuidador, para encontrarlo en casa: suena el
 * tono de llamada a todo volumen durante [SECONDS] s, aunque esté en silencio (usa el
 * canal de alarma). El cuidador lo puede parar antes ([stop], orden "stopRing").
 * Tocar la pantalla no lo para: es corto a propósito.
 */
internal object FindPhone {
    private const val SECONDS = 30
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    /** Volumen de alarma de antes de sonar, para dejarlo como estaba (null = no suena). */
    private var previousVolume: Int? = null
    private val timeout = Runnable { appContext?.let { stop(it) } }

    /** Si está sonando ahora (se publica en el estado para la web del cuidador). */
    val isRinging: Boolean get() = player != null

    fun ring(context: Context) {
        val app = context.applicationContext
        appContext = app
        // Si ya sonaba, vuelve a empezar la cuenta (sin perder el volumen de antes).
        handler.removeCallbacks(timeout)
        releasePlayer()
        val am = app.getSystemService(AudioManager::class.java)
        if (previousVolume == null) previousVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(app, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("FindPhone", "No se pudo hacer sonar", e)
            stop(app)
            throw e
        }
        handler.postDelayed(timeout, SECONDS * 1000L)
    }

    /** Deja de sonar (antes de tiempo o al acabar) y devuelve el volumen de alarma. */
    fun stop(context: Context) {
        handler.removeCallbacks(timeout)
        val wasRinging = player != null
        releasePlayer()
        previousVolume?.let {
            context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
        }
        previousVolume = null
        // Al acabar solo, la web tiene que enterarse de que ya no suena.
        if (wasRinging) Remote.publishSoon(context)
    }

    private fun releasePlayer() {
        player?.runCatching { stop(); release() }
        player = null
    }
}
