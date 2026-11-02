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
 * canal de alarma). Tocar la pantalla no lo para: es corto a propósito.
 */
internal object FindPhone {
    private const val SECONDS = 30
    private var player: MediaPlayer? = null

    fun ring(context: Context) {
        stop(context)
        val am = context.getSystemService(AudioManager::class.java)
        val previous = am.getStreamVolume(AudioManager.STREAM_ALARM)
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.w("FindPhone", "No se pudo hacer sonar", e)
        }
        Handler(Looper.getMainLooper()).postDelayed({
            stop(context)
            am.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0)
        }, SECONDS * 1000L)
    }

    private fun stop(context: Context) {
        player?.runCatching { stop(); release() }
        player = null
    }
}
