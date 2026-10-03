package com.launcherfacil.app.config

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadFavoriteContacts
import java.security.MessageDigest
import java.security.SecureRandom

/** Configuración del cuidador, guardada en SharedPreferences. */
class CaregiverSettings(context: Context) {

    companion object {
        val DEFAULT_KEYWORDS = listOf("UBICACION", "DONDE ESTAS")
        const val PIN_LENGTH = 4

        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_PIN_SALT = "pin_salt"
        private const val KEY_SOS_CONTACT = "sos_contact_id"
        private const val KEY_LOCATE_CONTACTS = "locate_contact_ids"
        private const val KEY_KEYWORDS = "keywords"
    }

    private val prefs = context.applicationContext
        .getSharedPreferences("caregiver", Context.MODE_PRIVATE)

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

    // --- Palabras clave para pedir la ubicación ---

    var keywords: List<String>
        get() = prefs.getString(KEY_KEYWORDS, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_KEYWORDS
        set(value) = prefs.edit { putString(KEY_KEYWORDS, value.joinToString(",")) }
}

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
