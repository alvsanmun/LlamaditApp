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

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadFavoriteContacts
import java.security.MessageDigest
import java.security.SecureRandom

/** Configuración del cuidador, guardada en SharedPreferences. */
class CaregiverSettings(context: Context) {

    companion object {
        const val PIN_LENGTH = 4

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_SOS_CONTACT = "sos_contact_id"
        private const val KEY_LOCATE_CONTACTS = "locate_contact_ids"
        private const val KEY_KEYWORDS = "keywords"
        private const val KEY_KIOSK_PAUSED = "kiosk_paused"
        private const val KEY_CONTACT_ORDER = "contact_order"
        private const val KEY_WIFI_ENABLED = "wifi_enabled"
        private const val KEY_SOS_ICON = "sos_icon"
        private const val KEY_HOME_LAYOUT = "home_layout"
        private const val KEY_BACK_BUTTON = "back_button"
        private const val KEY_HOUSE_LAT = "house_lat"
        private const val KEY_HOUSE_LNG = "house_lng"
        private const val KEY_HOUSE_ADDRESS = "house_address"
        private const val KEY_GO_HOME_ALERT = "go_home_alert"
        private const val KEY_GO_HOME_UPDATES = "go_home_updates"
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("caregiver", Context.MODE_PRIVATE)

    /** Palabras clave por defecto en el idioma del móvil (UBICACION, LOCATION, POSITION…). */
    private val defaultKeywords: List<String>
        get() = appContext.resources.getStringArray(R.array.default_keywords).toList()

    // --- PIN (se guarda solo el hash con sal, nunca el PIN en claro) ---

    val hasPin: Boolean get() = prefs.contains(KEY_PIN_HASH)

    fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit {
            putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_PIN_HASH, hash(salt, pin))
        }
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString(KEY_PIN_SALT, null) ?: return false
        val expected = prefs.getString(KEY_PIN_HASH, null) ?: return false
        return MessageDigest.isEqual(
            hash(Base64.decode(salt, Base64.NO_WRAP), pin).toByteArray(),
            expected.toByteArray()
        )
    }

    private fun hash(salt: ByteArray, pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray())
        }
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    // --- Contacto del SOS (null = el primer favorito) ---

    var sosContactId: Long?
        get() = prefs.getLong(KEY_SOS_CONTACT, -1).takeIf { it >= 0 }
        set(value) = prefs.edit {
            if (value == null) remove(KEY_SOS_CONTACT) else putLong(KEY_SOS_CONTACT, value)
        }

    // --- Quién puede pedir la ubicación (null = todos los favoritos) ---

    var locateContactIds: Set<Long>?
        get() = prefs.getStringSet(KEY_LOCATE_CONTACTS, null)?.mapNotNull { it.toLongOrNull() }?.toSet()
        set(value) = prefs.edit {
            if (value == null) remove(KEY_LOCATE_CONTACTS)
            else putStringSet(KEY_LOCATE_CONTACTS, value.map { it.toString() }.toSet())
        }

    // --- Modo quiosco en pausa (el cuidador lo usa para instalar o configurar) ---

    var kioskPaused: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_PAUSED, false)
        set(value) = prefs.edit { putBoolean(KEY_KIOSK_PAUSED, value) }

    // --- Orden de los favoritos (el mismo en Llamar y en Videollamada) ---

    /** Ids de contacto en el orden elegido; los que no estén van detrás, por nombre. */
    var contactOrder: List<Long>
        get() = prefs.getString(KEY_CONTACT_ORDER, null)
            ?.split(",")?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        set(value) = prefs.edit { putString(KEY_CONTACT_ORDER, value.joinToString(",")) }

    // --- Wi-Fi (por defecto apagado: el móvil va solo con la SIM) ---

    var wifiEnabled: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_WIFI_ENABLED, value) }

    // --- Dibujo del botón SOS (por defecto, las letras "SOS") ---

    var sosIcon: SosIcon
        get() = SosIcon.entries.firstOrNull { it.name == prefs.getString(KEY_SOS_ICON, null) } ?: SosIcon.TEXT
        set(value) = prefs.edit { putString(KEY_SOS_ICON, value.name) }

    // --- Botón para volver: la casa grande de arriba (por defecto) o una flecha abajo ---

    var backButton: BackButton
        get() = BackButton.entries.firstOrNull { it.name == prefs.getString(KEY_BACK_BUTTON, null) } ?: BackButton.HOUSE_TOP
        set(value) = prefs.edit { putString(KEY_BACK_BUTTON, value.name) }

    // --- Diseño de la pantalla principal (orden, tamaño y botones visibles) ---

    var homeLayout: List<HomeEntry>
        get() = decodeHomeLayout(prefs.getString(KEY_HOME_LAYOUT, null))
        set(value) = prefs.edit { putString(KEY_HOME_LAYOUT, encodeHomeLayout(value)) }

    // --- Casa de la persona (botón "Volver a casa") ---

    /** Coordenadas de la casa, o null si el cuidador aún no la ha marcado. */
    var housePosition: Pair<Double, Double>?
        get() {
            val lat = prefs.getString(KEY_HOUSE_LAT, null)?.toDoubleOrNull() ?: return null
            val lng = prefs.getString(KEY_HOUSE_LNG, null)?.toDoubleOrNull() ?: return null
            return lat to lng
        }
        set(value) = prefs.edit {
            if (value == null) {
                remove(KEY_HOUSE_LAT)
                remove(KEY_HOUSE_LNG)
            } else {
                putString(KEY_HOUSE_LAT, value.first.toString())
                putString(KEY_HOUSE_LNG, value.second.toString())
            }
        }

    /** Dirección que se enseña en grande (la escribe o corrige el cuidador). */
    var houseAddress: String
        get() = prefs.getString(KEY_HOUSE_ADDRESS, null).orEmpty()
        set(value) = prefs.edit { putString(KEY_HOUSE_ADDRESS, value) }

    /** Avisar por SMS al contacto del SOS cuando la persona pulsa "Volver a casa". */
    var goHomeAlert: Boolean
        get() = prefs.getBoolean(KEY_GO_HOME_ALERT, true)
        set(value) = prefs.edit { putBoolean(KEY_GO_HOME_ALERT, value) }

    /** Durante la ruta a casa, enviar la ubicación por SMS cada 10 minutos hasta que llegue. */
    var goHomeUpdates: Boolean
        get() = prefs.getBoolean(KEY_GO_HOME_UPDATES, false)
        set(value) = prefs.edit { putBoolean(KEY_GO_HOME_UPDATES, value) }

    /** Hace falta Google Maps: el botón de casa se ve o la casa está marcada. */
    val needsMaps: Boolean
        get() = housePosition != null || homeLayout.any { it.item == HomeItem.GO_HOME && it.visible }

    // --- Palabras clave para pedir la ubicación ---

    var keywords: List<String>
        get() = prefs.getString(KEY_KEYWORDS, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultKeywords
        set(value) = prefs.edit { putString(KEY_KEYWORDS, value.joinToString(",")) }
}

/**
 * Cómo se vuelve atrás en las pantallas: la barra grande con la casa, arriba, o una
 * flecha fija abajo a la izquierda (como el botón Atrás de Android, pero más grande).
 */
enum class BackButton { HOUSE_TOP, ARROW_BOTTOM }

/** Dibujos posibles para el botón SOS, para quien no reconozca las letras. */
enum class SosIcon { TEXT, AMBULANCE, CROSS, SIREN, HEART, WARNING }

/** Contacto del SOS: el elegido por el cuidador o, si no hay, el primer favorito. */
suspend fun resolveSosContact(context: Context): Contact? {
    val favorites = loadFavoriteContacts(context)
    val chosen = CaregiverSettings(context).sosContactId
    return favorites.firstOrNull { it.id == chosen } ?: favorites.firstOrNull()
}

/** Contactos que pueden pedir la ubicación por SMS. */
suspend fun locateContacts(context: Context): List<Contact> {
    val favorites = loadFavoriteContacts(context)
    val allowed = CaregiverSettings(context).locateContactIds ?: return favorites
    return favorites.filter { it.id in allowed }
}

/**
 * Ordena según el orden del cuidador. Los contactos que no ha colocado (favoritos
 * nuevos) quedan al final en el orden que ya traían (alfabético).
 */
fun <T> List<T>.inCaregiverOrder(context: Context, id: (T) -> Long): List<T> {
    val position = CaregiverSettings(context).contactOrder.withIndex().associate { it.value to it.index }
    return sortedBy { position[id(it)] ?: Int.MAX_VALUE }
}
