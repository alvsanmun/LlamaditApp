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

package com.launcherfacil.app.radio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.launcherfacil.app.R
import com.launcherfacil.app.volume.VolumeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Lo que se ve en la pantalla de la radio: la emisora que suena ([playing]), si aún
 * está cargando y la última que no se ha podido poner ([failed]).
 */
data class RadioState(val playing: String? = null, val buffering: Boolean = false, val failed: String? = null)

/** La radio, para el resto de la app. Todo se llama desde el hilo principal. */
object Radio {
    internal val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state

    /** Está sonando (o cargando) una emisora: los botones de volumen son los de la radio. */
    val isActive: Boolean get() = _state.value.playing != null

    fun play(context: Context, uuid: String) {
        RadioService.instance?.let { it.play(uuid); return }
        try {
            context.startForegroundService(
                Intent(context, RadioService::class.java).setAction(RadioService.ACTION_PLAY).putExtra(RadioService.EXTRA_UUID, uuid)
            )
        } catch (e: Exception) {
            Log.w("Radio", "No se pudo iniciar la radio", e)
        }
    }

    fun stop() {
        RadioService.instance?.stopRadio()
    }

    /** Hay alguna llamada (sonando o en curso): la radio calla hasta que se cuelga. */
    fun onCallsChanged(active: Boolean) {
        RadioService.instance?.onCall(active)
    }
}

/**
 * Radio por internet en segundo plano: sigue sonando con la pantalla apagada o desde
 * la pantalla principal, hasta que se pulsa el botón rojo o llega el apagado
 * automático que ha elegido el cuidador. Si la emisora se corta (falta cobertura), lo
 * vuelve a intentar unas veces; la primera, preguntando antes a radio-browser.info por
 * si ha cambiado de dirección.
 */
@OptIn(UnstableApi::class)
class RadioService : Service() {

    companion object {
        private const val TAG = "RadioService"
        internal const val ACTION_PLAY = "play"
        private const val ACTION_STOP = "stop"
        internal const val EXTRA_UUID = "uuid"
        private const val NOTIFICATION_ID = 4
        private const val CHANNEL_ID = "radio"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 4_000L
        private const val RESUME_DELAY_MS = 1_500L
        private const val RESUME_WINDOW_MS = 3_000L
        private const val MAX_RESUMES = 5

        internal var instance: RadioService? = null
            private set
    }

    private lateinit var player: ExoPlayer
    private val store by lazy { RadioStations(this) }
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val sleep = Runnable {
        Log.i(TAG, "Apagado automático")
        stopRadio()
    }
    private var station: Station? = null
    private var retries = 0
    private var inCall = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val http = DefaultHttpDataSource.Factory()
            // Muchas emisoras redirigen de http a https (o al revés).
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("LlamaditApp")
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this, http)))
            // Con el foco de audio, se baja o se calla sola si suena otra cosa (la voz de
            // Google Maps, un audio de WhatsApp…) y vuelve al terminar.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            // Sigue con la pantalla apagada.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        if (!startMediaForeground(store.find(intent?.getStringExtra(EXTRA_UUID).orEmpty())?.name.orEmpty())) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_PLAY -> play(intent.getStringExtra(EXTRA_UUID).orEmpty())
            else -> stopRadio()
        }
        return START_NOT_STICKY
    }

    internal fun play(uuid: String) {
        val chosen = store.find(uuid) ?: return stopRadio()
        station = chosen
        retries = 0
        Radio._state.value = RadioState(playing = uuid, buffering = true)
        startMediaForeground(chosen.name)
        // El volumen de la radio es el de multimedia, que la app tiene a cero.
        getSystemService(AudioManager::class.java)?.let { am ->
            try {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, store.volume(am), 0)
            } catch (e: SecurityException) {
                Log.w(TAG, "No se pudo poner el volumen de la radio", e)
            }
        }
        if (!inCall) start(chosen.url)
        // El apagado automático cuenta desde la última vez que se elige emisora.
        handler.removeCallbacks(sleep)
        store.sleepMinutes.takeIf { it > 0 }?.let { handler.postDelayed(sleep, it * 60_000L) }
        // Cuenta la escucha en el directorio y guarda la dirección nueva si ha cambiado.
        scope.launch {
            val url = RadioBrowser.resolve(uuid) ?: return@launch
            if (url != chosen.url) store.updateUrl(uuid, url)
        }
    }

    private fun start(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    internal fun onCall(active: Boolean) {
        if (active == inCall) return
        inCall = active
        if (active) {
            handler.removeCallbacks(retry)
            handler.removeCallbacks(resume)
            player.stop()
        } else if (station != null) {
            // Se espera un poco: el teléfono tarda en soltar el sonido al colgar y, si
            // la radio lo pide antes, Android se lo niega.
            Radio._state.value = Radio._state.value.copy(buffering = true)
            resumeAttempts = 0
            handler.postDelayed(resume, RESUME_DELAY_MS)
        }
    }

    private var resumeAttempts = 0
    private var resumedAt = 0L

    private val resume = Runnable {
        if (inCall || station == null) return@Runnable
        resumeAttempts++
        resumedAt = SystemClock.elapsedRealtime()
        // Una emisora en directo no se "reanuda": se vuelve a conectar.
        player.prepare()
        player.play()
    }

    private val retry = Runnable {
        val current = station ?: return@Runnable
        scope.launch {
            // La primera vez se pregunta si la emisora ha cambiado de dirección.
            val url = if (retries == 1) {
                RadioBrowser.resolve(current.uuid)?.also { store.updateUrl(current.uuid, it) } ?: current.url
            } else current.url
            station = current.copy(url = url)
            if (!inCall && station?.uuid == current.uuid) start(url)
        }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val uuid = station?.uuid ?: return
            if (playbackState == Player.STATE_READY) retries = 0
            Radio._state.value = RadioState(playing = uuid, buffering = playbackState == Player.STATE_BUFFERING)
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = station ?: return
            Log.w(TAG, "La emisora ${current.name} ha fallado (${error.errorCodeName})", error)
            if (retries < MAX_RETRIES) {
                retries++
                Radio._state.value = RadioState(playing = current.uuid, buffering = true)
                handler.postDelayed(retry, RETRY_DELAY_MS)
            } else {
                stopRadio(failed = current.uuid)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady || reason != Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) return
            // Recién colgada una llamada, el teléfono aún puede tener el sonido: otro intento.
            if (SystemClock.elapsedRealtime() - resumedAt < RESUME_WINDOW_MS && resumeAttempts < MAX_RESUMES) {
                handler.postDelayed(resume, RESUME_DELAY_MS)
                return
            }
            // Otra app se ha quedado con el sonido para siempre (no un momento): se apaga.
            stopRadio()
        }
    }

    internal fun stopRadio(failed: String? = null) {
        handler.removeCallbacksAndMessages(null)
        station = null
        // Desde aquí, una emisora nueva arranca el servicio otra vez.
        instance = null
        player.stop()
        Radio._state.value = RadioState(failed = failed)
        // Multimedia vuelve a cero, como en el resto de la app.
        VolumeController.applySoundPolicy(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        player.release()
        if (instance === this) instance = null
        if (Radio.isActive) {
            Radio._state.value = RadioState()
            VolumeController.applySoundPolicy(this)
        }
        super.onDestroy()
    }

    /** Notificación obligatoria de un servicio en primer plano (con el quiosco no se ve). */
    private fun startMediaForeground(stationName: String): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_radio), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 0, Intent(this, RadioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_radio)
            .setContentTitle(getString(R.string.home_item_radio))
            .setContentText(stationName)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.radio_stop), stop)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else 0
        return try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo pasar a primer plano", e)
            false
        }
    }
}
