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

package com.launcherfacil.app.alerts

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.pills.PillAlarms
import com.launcherfacil.app.pills.PillStore
import com.launcherfacil.app.ui.hideNavigationBar
import com.launcherfacil.app.volume.VolumeController
import com.launcherfacil.app.volume.WithVolumeOverlay

/**
 * Avisos a pantalla completa: batería baja y pastillas. Se abren encima de todo (también de
 * la pantalla de bloqueo) y solo se quitan con su botón: no hay Atrás.
 */
class ReminderActivity : ComponentActivity() {
    companion object {
        private const val EXTRA_KIND = "kind"
        private const val KIND_BATTERY = "battery"
        private const val KIND_PILLS = "pills"

        fun battery(context: Context) = Intent(context, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_KIND, KIND_BATTERY)

        /** [test]: lo abre el cuidador para probarlo; no cuenta como toma. */
        fun pills(context: Context, minutes: Int, test: Boolean = false) = Intent(context, ReminderActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_KIND, KIND_PILLS)
            .putExtra(PillAlarms.EXTRA_MINUTES, minutes)
            .putExtra(EXTRA_TEST, test)

        private const val EXTRA_TEST = "test"
    }

    // Un aviso de pastillas puede llegar con otro ya abierto: se cambia el contenido.
    private var kind by mutableStateOf(KIND_BATTERY)
    private var minutes by mutableIntStateOf(-1)
    private var test = false
    private var version by mutableIntStateOf(0)

    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        read(intent)
        setContent {
            BackHandler { }
            WithVolumeOverlay {
                if (kind == KIND_PILLS) {
                    PillsAlertScreen(
                        minutes = minutes,
                        version = version,
                        onDone = { answers ->
                            if (!test) PillAlarms.confirm(this, minutes, answers)
                            leave()
                        },
                        onLater = ::leave
                    )
                } else {
                    BatteryAlertScreen(percent = BatteryWatcher.percent, onDone = ::leave)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent) {
        kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_BATTERY
        minutes = intent.getIntExtra(PillAlarms.EXTRA_MINUTES, -1)
        test = intent.getBooleanExtra(EXTRA_TEST, false)
        version++
        // Las pastillas encienden la pantalla; la batería baja solo sale con ella encendida.
        setTurnScreenOn(kind == KIND_PILLS)
        if (kind == KIND_PILLS && PillStore(this).dosesAt(minutes).isEmpty()) {
            finish()
            return
        }
        attention(repeat = kind == KIND_PILLS)
    }

    /**
     * Sonido y vibración. Las pastillas suenan con el volumen de las alarmas, cada minuto
     * durante 5 minutos o hasta que se toque la pantalla.
     */
    private fun attention(repeat: Boolean) {
        handler.removeCallbacksAndMessages(null)
        var times = if (repeat) 5 else 1
        val beep = object : Runnable {
            override fun run() {
                ringtone?.stop()
                ringtone = RingtoneManager.getRingtone(this@ReminderActivity, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    ?.apply {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(if (repeat) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                            .build()
                        play()
                    }
                getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400), -1))
                if (--times > 0) handler.postDelayed(this, 60_000)
            }
        }
        beep.run()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        handler.removeCallbacksAndMessages(null)
    }

    /** Al quitar el aviso. En pausa, el cuidador vuelve a lo suyo y no a LlamaditApp. */
    private fun leave() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        if (CaregiverSettings(this).kioskPaused) moveTaskToBack(true)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        hideNavigationBar()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        VolumeController.onKeyEvent(this, event) || super.dispatchKeyEvent(event)
}
