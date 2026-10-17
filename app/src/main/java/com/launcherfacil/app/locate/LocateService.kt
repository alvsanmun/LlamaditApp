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

package com.launcherfacil.app.locate

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.launcherfacil.app.sos.currentLocation
import com.launcherfacil.app.sos.sendSms
import com.launcherfacil.app.sos.startLocationForeground
import com.launcherfacil.app.sos.withBattery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Responde a una petición de ubicación: una posición puntual y un SMS al
 * remitente. Nada de seguimiento: termina en cuanto envía la respuesta.
 */
class LocateService : Service() {

    companion object {
        private const val TAG = "LocateService"
        private const val EXTRA_REPLY_TO = "reply_to"
        private const val NOTIFICATION_ID = 2

        fun intent(context: Context, replyTo: String) =
            Intent(context, LocateService::class.java).putExtra(EXTRA_REPLY_TO, replyTo)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pending = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Si Android no deja pasar a primer plano (app en segundo plano y aún sin
        // Device Owner), hay que terminar antes de que salte el límite de tiempo:
        // se usa un tiempo de GPS corto.
        val foreground = startLocationForeground(NOTIFICATION_ID, "Enviando la ubicación por SMS")
        val gpsTimeoutMs = if (foreground) 20_000L else 4_000L
        val replyTo = intent?.getStringExtra(EXTRA_REPLY_TO)
        if (replyTo == null) {
            stopIfIdle()
            return START_NOT_STICKY
        }
        pending++
        scope.launch {
            val location = currentLocation(this@LocateService, gpsTimeoutMs)
            val text = withBattery(this@LocateService, locateReply(location))
            val sent = sendSms(replyTo, text)
            Log.i(TAG, "Respuesta a $replyTo enviada=$sent: $text")
            pending--
            stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle() {
        if (pending > 0) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
