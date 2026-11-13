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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.ui.toBatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Aviso de batería baja: cuando baja del nivel que elige el cuidador y el móvil no está
 * cargando, sale el dibujo del móvil con el cargador. Una vez por descarga: vuelve a estar
 * listo al enchufarlo. Con la pantalla apagada espera a que se encienda, para no despertar
 * a nadie. Vive mientras viva la app (el acceso a notificaciones la mantiene despierta).
 */
object BatteryWatcher {
    private const val TAG = "BatteryWatcher"
    private var receiver: BroadcastReceiver? = null
    private var armed = true
    private var pending = false

    /** Si está cargando: el aviso se cierra solo al enchufar el cargador. */
    private val _charging = MutableStateFlow(false)
    val charging: StateFlow<Boolean> = _charging

    var percent = -1
        private set

    fun start(context: Context) {
        if (receiver != null) return
        val app = context.applicationContext
        receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> onBattery(app, intent)
                    Intent.ACTION_SCREEN_ON -> if (pending) show(app)
                }
            }
        }
        app.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(Intent.ACTION_SCREEN_ON) })
    }

    private fun onBattery(context: Context, intent: Intent) {
        val battery = intent.toBatteryState()
        percent = battery.percent
        // Enchufado aunque aún no diga "cargando" (lo primero que cambia al conectar el cable).
        val plugged = battery.charging || intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        _charging.value = plugged
        val settings = CaregiverSettings(context)
        if (plugged || battery.percent > settings.lowBatteryPercent) {
            armed = true
            pending = false
            return
        }
        if (!armed || !settings.lowBatteryAlert) return
        armed = false
        val screenOn = context.getSystemService(PowerManager::class.java).isInteractive
        if (screenOn) show(context) else pending = true
    }

    private fun show(context: Context) {
        pending = false
        Log.i(TAG, "Aviso de batería baja: $percent %")
        try {
            context.startActivity(ReminderActivity.battery(context))
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo enseñar el aviso", e)
        }
    }
}
