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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat
import com.launcherfacil.app.sos.currentLocation
import com.launcherfacil.app.sos.sendSms
import com.launcherfacil.app.sos.withBattery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Escucha los SMS entrantes. Si un contacto autorizado envía la palabra clave,
 * responde con la ubicación. Los demás SMS se ignoran (la app de mensajes los
 * sigue recibiendo con normalidad).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val sender = messages.firstOrNull()?.originatingAddress ?: return
        // Un SMS largo llega en varias partes.
        val body = messages.joinToString("") { it.messageBody.orEmpty() }
        if (!isLocateRequest(context, body)) return

        val result = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (!isAuthorizedSender(context, sender)) {
                    Log.w(TAG, "Petición de ubicación de un número no autorizado: ignorada")
                    return@launch
                }
                try {
                    // Lo normal: un servicio en primer plano, que tiene tiempo para el GPS.
                    ContextCompat.startForegroundService(context, LocateService.intent(context, sender))
                } catch (e: Exception) {
                    // Android puede impedir arrancarlo en segundo plano (hasta que la app
                    // sea Device Owner, paso 6). Plan B: responder aquí con menos tiempo.
                    Log.w(TAG, "No se pudo arrancar LocateService; respuesta rápida", e)
                    val location = currentLocation(context, gpsTimeoutMs = 6_000)
                    val text = withBattery(context, locateReply(context, location))
                    val sent = context.sendSms(sender, text)
                    Log.i(TAG, "Respuesta rápida enviada=$sent: $text")
                }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
    }
}
