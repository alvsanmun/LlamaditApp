package com.launcherfacil.whatsappstub

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log

const val CALL_NOTIFICATION_ID = 42
private const val CHANNEL_ID = "calls"

/**
 * Simula una llamada entrante de WhatsApp con una notificación CallStyle, como
 * las apps de llamadas modernas:
 *
 *   adb shell am broadcast -n com.whatsapp/com.launcherfacil.whatsappstub.SimulateCallReceiver --es name "María" --ez video true
 */
class SimulateCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return // CallStyle: Android 12+
        val name = intent.getStringExtra("name") ?: "María"
        val video = intent.getBooleanExtra("video", true)
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Llamadas", NotificationManager.IMPORTANCE_HIGH)
        )

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val answer = PendingIntent.getActivity(
            context, 1,
            Intent(context, StubActivity::class.java).putExtra("answered", true).putExtra("video", video),
            flags
        )
        val decline = PendingIntent.getBroadcast(
            context, 2, Intent(context, DeclineReceiver::class.java), flags
        )
        // Lo que abriría WhatsApp a pantalla completa con el móvil bloqueado.
        val fullScreen = PendingIntent.getActivity(
            context, 3,
            Intent(context, StubActivity::class.java).putExtra("ringing", true).putExtra("video", video),
            flags
        )
        val caller = Person.Builder()
            .setName(name)
            .setIcon(Icon.createWithBitmap(profilePhoto(name)))
            .setImportant(true)
            .build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(name)
            .setContentText(if (video) "Videollamada entrante de WhatsApp" else "Llamada entrante de WhatsApp")
            .setCategory(Notification.CATEGORY_CALL)
            .setFullScreenIntent(fullScreen, true)
            .setOngoing(true)
            .setStyle(
                Notification.CallStyle.forIncomingCall(caller, decline, answer).setIsVideo(video)
            )
            .build()
        nm.notify(CALL_NOTIFICATION_ID, notification)
        Log.i("WhatsAppStub", "Llamada simulada de $name (vídeo=$video)")
    }

    /** "Foto de perfil de WhatsApp": un círculo con la inicial (distinta de la de la agenda). */
    private fun profilePhoto(name: String): Bitmap {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(37, 211, 102) }
        canvas.drawCircle(128f, 128f, 128f, paint)
        paint.color = Color.WHITE
        paint.textSize = 140f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(name.take(1), 128f, 176f, paint)
        return bitmap
    }
}

/** "Rechazar" en la notificación: cuelga (quita la notificación). */
class DeclineReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFICATION_ID)
        Log.i("WhatsAppStub", "LLAMADA RECHAZADA")
    }
}
