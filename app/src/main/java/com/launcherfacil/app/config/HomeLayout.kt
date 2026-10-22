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

package com.launcherfacil.app.config

/** Elementos de la pantalla principal que el cuidador puede colocar. */
enum class HomeItem(val isButton: Boolean) {
    CLOCK(false), BATTERY(false),
    CALL(true), WHATSAPP(true), DIALPAD(true), SOS(true), MESSAGES(true), MISSED_CALLS(true), GO_HOME(true), WHATSAPP_APP(true)
}

/**
 * Tamaño de un botón. [SMALL] y [MEDIUM] ocupan media fila (se emparejan con el
 * siguiente); [LARGE] ocupa la fila entera. Los pequeños son bajos; los medianos y
 * grandes se reparten el alto que queda.
 */
enum class ButtonSize { SMALL, MEDIUM, LARGE }

data class HomeEntry(val item: HomeItem, val size: ButtonSize = ButtonSize.MEDIUM, val visible: Boolean = true)

/** Diseño original: reloj, batería, cuatro botones grandes y dos pequeños debajo. */
val DEFAULT_HOME_LAYOUT = listOf(
    HomeEntry(HomeItem.CLOCK),
    HomeEntry(HomeItem.BATTERY),
    HomeEntry(HomeItem.CALL),
    HomeEntry(HomeItem.WHATSAPP),
    HomeEntry(HomeItem.DIALPAD),
    HomeEntry(HomeItem.SOS),
    HomeEntry(HomeItem.MESSAGES, ButtonSize.SMALL),
    HomeEntry(HomeItem.MISSED_CALLS, ButtonSize.SMALL),
    // "Volver a casa": desactivado hasta que el cuidador lo active.
    HomeEntry(HomeItem.GO_HOME, visible = false),
    // WhatsApp completo (chats): desactivado hasta que el cuidador lo active.
    HomeEntry(HomeItem.WHATSAPP_APP, visible = false),
)

/** Una fila de la pantalla: uno o dos elementos. */
data class HomeRow(val entries: List<HomeEntry>) {
    /** Los botones medianos o grandes se reparten el alto libre; los pequeños son bajos. */
    val stretches: Boolean get() = entries.any { it.item.isButton && it.size != ButtonSize.SMALL }
    val isButtons: Boolean get() = entries.first().item.isButton
}

/**
 * Coloca los elementos visibles en filas, en orden. El reloj y la batería van solos.
 * Un botón grande ocupa su fila; los pequeños y medianos se emparejan con el siguiente
 * si también es de media fila (si se queda solo, ocupa la fila entera).
 */
fun List<HomeEntry>.toRows(): List<HomeRow> {
    val rows = mutableListOf<HomeRow>()
    var pending: HomeEntry? = null
    fun flush() { pending?.let { rows += HomeRow(listOf(it)) }; pending = null }
    for (entry in filter { it.visible || it.item == HomeItem.CLOCK }) {
        val half = entry.item.isButton && entry.size != ButtonSize.LARGE
        if (!half) {
            flush()
            rows += HomeRow(listOf(entry))
        } else if (pending == null) {
            pending = entry
        } else {
            rows += HomeRow(listOf(pending!!, entry))
            pending = null
        }
    }
    flush()
    return rows
}

/** Formato guardado: "CLOCK:MEDIUM:1,BATTERY:MEDIUM:1,CALL:LARGE:0,…". */
internal fun encodeHomeLayout(layout: List<HomeEntry>): String =
    layout.joinToString(",") { "${it.item.name}:${it.size.name}:${if (it.visible) 1 else 0}" }

/**
 * Lee el diseño guardado tolerando datos raros: ignora lo desconocido, añade al final
 * los elementos que falten (por ejemplo, de una versión nueva) y el reloj siempre se ve,
 * porque es la entrada al panel del cuidador.
 */
internal fun decodeHomeLayout(text: String?): List<HomeEntry> {
    if (text.isNullOrBlank()) return DEFAULT_HOME_LAYOUT
    val parsed = text.split(",").mapNotNull { part ->
        val fields = part.split(":")
        val item = HomeItem.entries.firstOrNull { it.name == fields.getOrNull(0) } ?: return@mapNotNull null
        val size = ButtonSize.entries.firstOrNull { it.name == fields.getOrNull(1) } ?: ButtonSize.MEDIUM
        HomeEntry(item, size, fields.getOrNull(2) != "0")
    }.distinctBy { it.item }
    val missing = DEFAULT_HOME_LAYOUT.filter { default -> parsed.none { it.item == default.item } }
    return (parsed + missing).map { if (it.item == HomeItem.CLOCK) it.copy(visible = true) else it }
}
