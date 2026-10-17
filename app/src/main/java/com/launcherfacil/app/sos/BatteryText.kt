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
