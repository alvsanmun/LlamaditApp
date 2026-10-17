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

package com.launcherfacil.app

import android.Manifest
import android.os.Build

/** Permisos que la app pide (o se concede sola si es Device Owner). */
val APP_PERMISSIONS: List<String> = buildList {
    add(Manifest.permission.READ_CONTACTS)
    // Marcar "Tiene WhatsApp": el launcher escribe la fila de videollamada en el contacto.
    add(Manifest.permission.WRITE_CONTACTS)
    add(Manifest.permission.CALL_PHONE)
    add(Manifest.permission.SEND_SMS)
    add(Manifest.permission.RECEIVE_SMS)
    add(Manifest.permission.READ_PHONE_STATE)
    // Botones de mensajes y llamadas perdidas.
    add(Manifest.permission.READ_SMS)
    add(Manifest.permission.READ_CALL_LOG)
    add(Manifest.permission.WRITE_CALL_LOG)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    // Para que se vea la notificación de "SOS activo".
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}

/**
 * Ubicación con la pantalla apagada. No se puede pedir junto a las demás (Android
 * manda a Ajustes); como Device Owner se concede sin preguntar.
 */
const val BACKGROUND_LOCATION = Manifest.permission.ACCESS_BACKGROUND_LOCATION

/** Permisos que necesita WhatsApp para las videollamadas. */
val WHATSAPP_PERMISSIONS = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.READ_CONTACTS
)
