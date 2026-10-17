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

package com.launcherfacil.app.whatsapp

import android.app.Notification
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.launcherfacil.app.MainActivity
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

    /** Notificaciones de llamada de WhatsApp en curso (entrante contestada o saliente). */
    private val ongoingKeys = mutableSetOf<String>()

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return
        // Contestada, rechazada o perdida: se cierra nuestra pantalla.
        WhatsAppIncoming.clear(sbn.key)
        // Fin de una videollamada: WhatsApp se quedaría en su pantalla; volvemos al launcher.
        if (ongoingKeys.remove(sbn.key) && ongoingKeys.isEmpty()) {
            Log.i(TAG, "Fin de la llamada de WhatsApp: vuelta al launcher")
            handler.postDelayed(::goHome, 500)
        }
    }

    private fun goHome() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo volver al launcher", e)
        }
    }

    private fun check(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return
        logWhatsAppNotification(sbn)
        if (isOngoingCall(sbn)) ongoingKeys += sbn.key
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
