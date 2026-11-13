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

package com.launcherfacil.app.kiosk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.launcherfacil.app.MainActivity
import com.launcherfacil.app.R
import com.launcherfacil.app.alerts.BatteryWatcher
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.pills.PillAlarms
import com.launcherfacil.app.remote.Remote

/**
 * Aviso fijo mientras el quiosco está en pausa: recuerda al cuidador que el móvil no está
 * protegido y, al tocarlo, abre LlamaditApp para volver a activarlo.
 */
object PausedNotice {
    private const val CHANNEL_ID = "kiosk"
    private const val NOTIFICATION_ID = 4

    fun show(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_kiosk), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )
        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home)
                .setContentTitle(context.getString(R.string.cg_status_paused))
                .setContentText(context.getString(R.string.kiosk_paused_notification))
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        )
    }

    fun hide(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }
}

/**
 * En pausa, LlamaditApp no es la pantalla de inicio y no se abre sola al encender: aquí se
 * recupera lo que normalmente arranca con ella (también tras instalar una versión nueva).
 * Con el quiosco activo, instalar una versión cierra la app y el móvil se quedaría fuera de
 * él hasta que alguien pulsara Inicio: se vuelve a abrir aquí, y se bloquea al abrirse.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Las alarmas se borran al apagar el móvil (y la hora puede haber cambiado).
        PillAlarms.schedule(context)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) return
        BatteryWatcher.start(context)
        if (CaregiverSettings(context).kioskPaused) {
            PausedNotice.show(context)
            Remote.start(context)
        } else if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED && Kiosk.isDeviceOwner(context)) {
            context.startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
