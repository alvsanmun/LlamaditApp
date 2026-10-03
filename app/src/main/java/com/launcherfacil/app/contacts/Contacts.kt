package com.launcherfacil.app.contacts

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(val id: Long, val name: String, val phone: String)

/**
 * Contactos favoritos (★) de la agenda, con un número por contacto
 * (el principal si lo hay). El cuidador los gestiona desde la app Contactos.
 */
suspend fun loadFavoriteContacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
    val projection = arrayOf(
        Phone.CONTACT_ID,
        Phone.DISPLAY_NAME_PRIMARY,
        Phone.NUMBER,
        Phone.IS_SUPER_PRIMARY,
        Phone.IS_PRIMARY
    )
    val byContact = linkedMapOf<Long, Pair<Contact, Int>>()
    context.contentResolver.query(
        Phone.CONTENT_URI,
        projection,
        "${Phone.STARRED} = 1",
        null,
        "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val number = c.getString(2) ?: continue
            val rank = c.getInt(3) * 2 + c.getInt(4)
            val current = byContact[id]
            if (current == null || rank > current.second) {
                byContact[id] = Contact(id, c.getString(1).orEmpty(), number) to rank
            }
        }
    }
    byContact.values.map { it.first }
}

/** Foto del contacto (en alta resolución si existe), o null si no tiene. */
suspend fun loadContactPhoto(context: Context, contactId: Long): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)
            ?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    }

/** Busca en la agenda el contacto de un número (para la pantalla de llamada). */
suspend fun findContactByNumber(context: Context, number: String): Contact? =
    withContext(Dispatchers.IO) {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                if (c.moveToFirst()) Contact(c.getLong(0), c.getString(1).orEmpty(), number) else null
            }
        } catch (e: SecurityException) {
            null // Sin permiso de contactos: se mostrará solo el número.
        }
    }

/** Busca un favorito por su nombre (WhatsApp muestra el nombre de la agenda). */
suspend fun findFavoriteByName(context: Context, name: String): Contact? =
    loadFavoriteContacts(context).firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
