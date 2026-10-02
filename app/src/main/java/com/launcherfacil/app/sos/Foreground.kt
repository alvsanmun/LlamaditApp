package com.launcherfacil.app.sos

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.launcherfacil.app.R

private const val CHANNEL_ID = "location"

/**
 * Pasa el servicio a primer plano con tipo "location" (necesario para usar el GPS
 * con la app en segundo plano). Devuelve false si Android no lo permite ahora mismo;
 * en ese caso el servicio debe terminar enseguida.
 */
fun Service.startLocationForeground(notificationId: Int, text: String): Boolean {
    val manager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Ubicación", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }
    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_call)
        .setContentTitle(getString(R.string.app_name))
        .setContentText(text)
        .setOngoing(true)
        .build()
    val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
    } else 0
    return try {
        ServiceCompat.startForeground(this, notificationId, notification, type)
        true
    } catch (e: Exception) {
        Log.w("Foreground", "No se pudo pasar a primer plano", e)
        false
    }
}
