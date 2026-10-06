package com.launcherfacil.app.whatsapp

import android.app.Notification
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.launcherfacil.app.contacts.WHATSAPP_PACKAGE

/**
 * Lee las notificaciones de WhatsApp. Cuando entra una llamada, abre nuestra
 * pantalla (foto grande, botones verde y rojo) en lugar de la de WhatsApp, que
 * dentro del quiosco no se vería con la pantalla encendida.
 *
 * Necesita "acceso a notificaciones": se activa una vez con
 *   adb shell cmd notification allow_listener com.launcherfacil.app/com.launcherfacil.app.whatsapp.WhatsAppCallListener
 * o desde la configuración del cuidador.
 */
class WhatsAppCallListener : NotificationListenerService() {

    private val handler = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        activeNotifications?.forEach(::check)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = check(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Contestada, rechazada o perdida: se cierra nuestra pantalla.
        if (sbn.packageName == WHATSAPP_PACKAGE) WhatsAppIncoming.clear(sbn.key)
    }

    private fun check(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return
        logWhatsAppNotification(sbn)
        val call = parseWhatsAppCall(this, sbn)
        if (call == null) {
            // Cualquier otra notificación de llamada (sobre todo "Videollamada en curso",
            // tras contestar) significa que ya no está sonando: se cierra nuestra
            // pantalla para que se vea la de WhatsApp.
            if (sbn.notification.category == Notification.CATEGORY_CALL) WhatsAppIncoming.clear()
            return
        }
        Log.i(TAG, "Llamada entrante de WhatsApp: ${call.name} (vídeo=${call.isVideo})")
        if (!WhatsAppIncoming.show(call)) {
            Log.i(TAG, "Recién contestada o rechazada: se ignora")
            return
        }
        // Con la pantalla apagada, WhatsApp abre su propia pantalla a la vez que
        // nosotros: se vuelve a traer la nuestra al frente un par de veces.
        for (delayMs in listOf(0L, 700L, 1500L)) {
            handler.postDelayed({ openScreen(call.key) }, delayMs)
        }
    }

    private fun openScreen(key: String) {
        if (WhatsAppIncoming.call.value?.key != key) return
        try {
            startActivity(
                Intent(this, WhatsAppCallActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo abrir la pantalla de llamada de WhatsApp", e)
        }
    }

    private companion object {
        const val TAG = "WhatsAppCallListener"
    }
}
