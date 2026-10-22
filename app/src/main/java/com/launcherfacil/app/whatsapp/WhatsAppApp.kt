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

import android.app.Activity
import android.app.Notification
import android.content.ActivityNotFoundException
import android.service.notification.StatusBarNotification
import android.util.Log
import com.launcherfacil.app.contacts.WHATSAPP_PACKAGE
import com.launcherfacil.app.volume.VolumeController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * WhatsApp completo (chats, audios, fotos y vídeos), para quien lo quiera además de
 * las videollamadas. Se abre desde un botón propio; mientras se usa, una pestaña con
 * una casa en el borde de la pantalla ([WhatsAppHomeButton]) devuelve a LlamaditApp.
 */
object WhatsAppApp {
    private const val TAG = "WhatsAppApp"

    /** Mensajes de WhatsApp sin leer (los de sus notificaciones), para el número rojo. */
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread

    /** WhatsApp está abierto desde nuestro botón: hay que enseñar la casa para volver. */
    @Volatile
    var active = false
        private set

    fun open(activity: Activity): Boolean {
        val intent = activity.packageManager.getLaunchIntentForPackage(WHATSAPP_PACKAGE) ?: return false
        return try {
            // Las notas de voz y los vídeos suenan por el volumen de multimedia, que la
            // app deja a cero; vuelve a cero al regresar al launcher.
            VolumeController.raiseMediaVolume(activity)
            activity.startActivity(intent)
            active = true
            WhatsAppHomeButton.show(activity.applicationContext)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No se pudo abrir WhatsApp", e)
            false
        }
    }

    /** El launcher vuelve a verse (con la casa, con Atrás o al colgar): se acabó. */
    fun onLauncherVisible() {
        active = false
        WhatsAppHomeButton.hide()
    }

    /**
     * Cuenta los mensajes sin leer en las notificaciones de WhatsApp: una por chat, con
     * sus mensajes (MessagingStyle). Se ignoran el resumen del grupo y las llamadas.
     * WhatsApp quita la notificación al abrir el chat, así que el número baja solo.
     */
    fun updateUnread(notifications: Array<StatusBarNotification>?) {
        _unread.value = notifications.orEmpty()
            .filter { it.packageName == WHATSAPP_PACKAGE }
            .map { it.notification }
            .filter { it.category == Notification.CATEGORY_MESSAGE && it.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .sumOf { n ->
                @Suppress("DEPRECATION")
                val messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES)
                messages?.size?.takeIf { it > 0 } ?: 1
            }
    }
}
