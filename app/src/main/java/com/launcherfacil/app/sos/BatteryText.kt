package com.launcherfacil.app.sos

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.launcherfacil.app.ui.toBatteryState

/**
 * Nivel de batería para añadir a los SMS, p. ej. "Batería 45 %" o
 * "Batería 80 % (cargando)". Así el cuidador sabe si el móvil se va a apagar.
 * Usa el mismo broadcast que el indicador de la pantalla, para que coincidan.
 */
fun batteryText(context: Context): String {
    // ACTION_BATTERY_CHANGED es "sticky": con receptor null devuelve el último valor.
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return ""
    val battery = intent.toBatteryState()
    return if (battery.charging) "Batería ${battery.percent} % (cargando)" else "Batería ${battery.percent} %"
}

/** Añade " · Batería N %" al final del texto (si se puede leer la batería). */
fun withBattery(context: Context, text: String): String {
    val battery = batteryText(context)
    return if (battery.isEmpty()) text else "$text · $battery"
}
