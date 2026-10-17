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

package com.launcherfacil.app.call

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log

/**
 * Android nos entrega aquí todas las llamadas normales (entrantes y salientes)
 * porque somos la app de Teléfono predeterminada. Mostramos nuestra pantalla y
 * ponemos nosotros el tono ([CallRinger]) y la vibración ([RingVibrator]).
 */
class CallService : InCallService() {

    override fun onCallAdded(call: Call) {
        CallManager.attach(this)
        CallManager.add(call)
        try {
            startActivity(
                Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.e("CallService", "No se pudo abrir la pantalla de llamada", e)
        }
    }

    override fun onCallRemoved(call: Call) {
        CallManager.remove(call)
    }

    @Deprecated("Sustituido por onCallEndpointChanged en Android 14, pero sigue llamándose")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        CallManager.onAudioStateChanged(audioState)
    }

    /**
     * Android lo pide al pulsar los botones de volumen (o el de encendido) mientras
     * suena una llamada. A propósito NO se silencia: la persona podría perder la
     * llamada sin darse cuenta.
     */
    override fun onSilenceRinger() {
        Log.i("CallService", "Petición de silenciar el tono: se ignora")
    }

    override fun onDestroy() {
        CallManager.detach(this)
        super.onDestroy()
    }
}
