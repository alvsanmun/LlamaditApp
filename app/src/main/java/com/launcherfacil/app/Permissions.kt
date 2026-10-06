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
