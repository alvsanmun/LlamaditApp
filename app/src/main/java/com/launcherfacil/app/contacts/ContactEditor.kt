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

package com.launcherfacil.app.contacts

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import com.launcherfacil.app.gohome.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// Alta y cambios de personas sin abrir la app Contactos: se escribe directamente en la
// agenda. Abrir Contactos dentro del quiosco obligaba a salir de él y, al volver, el
// launcher se quedaba en un estado raro (pedido el 2026-11-02).

private const val TAG = "ContactEditor"

/** Lado de la foto que se guarda: Contactos genera a partir de ella la miniatura y la grande. */
private const val PHOTO_SIDE = 720

/** Foto elegida en el formulario, en caché hasta que se guarda la persona. */
fun draftPhotoFile(context: Context) = File(context.cacheDir, "person/photo.jpg")

/** Recorta al centro en cuadrado y guarda la foto elegida como borrador. */
suspend fun saveDraftPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = decodeScaled(context, uri, PHOTO_SIDE * 2) ?: return@withContext false
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = if (side > PHOTO_SIDE) Bitmap.createScaledBitmap(square, PHOTO_SIDE, PHOTO_SIDE, true) else square
        val file = draftPhotoFile(context).apply { parentFile?.mkdirs() }
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        true
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo abrir la foto", e)
        false
    }
}

suspend fun loadDraftPhoto(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    draftPhotoFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}

private fun Bitmap.toJpeg(): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

/** Cifras de un teléfono, para comprobar que es válido (como el teclado: 3 como mínimo). */
fun isValidPhone(phone: String): Boolean = phone.count { it.isDigit() } >= 3

/**
 * Crea la persona en la agenda, ya con la estrella para que salga en LlamaditApp.
 * [googleAccount] = cuenta de Google donde guardarla (se sincroniza con
 * contacts.google.com) o null para guardarla solo en el móvil. Devuelve el id del
 * contacto, o null si no se pudo.
 */
suspend fun createPerson(
    context: Context,
    name: String,
    phone: String,
    photo: Bitmap?,
    googleAccount: String?
): Long? = withContext(Dispatchers.IO) {
    val ops = arrayListOf<ContentProviderOperation>()
    ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        .withValue(RawContacts.ACCOUNT_TYPE, googleAccount?.let { GOOGLE_ACCOUNT_TYPE })
        .withValue(RawContacts.ACCOUNT_NAME, googleAccount)
        .withValue(RawContacts.STARRED, 1)
        .build()
    ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        .withValue(StructuredName.DISPLAY_NAME, name.trim())
        .build()
    ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
        .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
        .withValue(Phone.NUMBER, phone.trim())
        .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
        .withValue(Data.IS_PRIMARY, 1)
        .withValue(Data.IS_SUPER_PRIMARY, 1)
        .build()
    if (photo != null) {
        ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
            .withValueBackReference(Data.RAW_CONTACT_ID, 0)
            .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
            .withValue(Photo.PHOTO, photo.toJpeg())
            .build()
    }
    try {
        val results = context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        val rawId = results.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return@withContext null
        val contactId = contactIdOfRaw(context, rawId) ?: return@withContext null
        // La estrella va en el contacto (por si Android lo ha unido a otro que ya existía).
        setStarred(context, contactId, true)
        contactId
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo crear el contacto", e)
        null
    }
}

private fun contactIdOfRaw(context: Context, rawId: Long): Long? =
    context.contentResolver.query(
        ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId),
        arrayOf(RawContacts.CONTACT_ID), null, null, null
    )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null }

/** Contactos "en bruto" de un contacto que no son de WhatsApp (los del móvil o de Google). */
internal fun editableRawIds(context: Context, contactId: Long): List<Long> {
    val ids = mutableListOf<Long>()
    context.contentResolver.query(
        RawContacts.CONTENT_URI,
        arrayOf(RawContacts._ID),
        "${RawContacts.CONTACT_ID} = ? AND ${RawContacts.DELETED} = 0 AND " +
            "(${RawContacts.ACCOUNT_TYPE} IS NULL OR ${RawContacts.ACCOUNT_TYPE} != ?)",
        arrayOf(contactId.toString(), WHATSAPP_PACKAGE), null
    )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
    return ids
}

/** Qué hacer con la foto al guardar los cambios de una persona. */
enum class PhotoChange { KEEP, REPLACE, REMOVE }

/**
 * Cambia el nombre, el teléfono ([oldPhone] es el que se ve en LlamaditApp) y la foto de
 * una persona que ya está en la agenda.
 */
suspend fun updatePerson(
    context: Context,
    contactId: Long,
    oldPhone: String,
    name: String,
    phone: String,
    photoChange: PhotoChange,
    photo: Bitmap?
): Boolean = withContext(Dispatchers.IO) {
    try {
        val rawIds = editableRawIds(context, contactId)
        if (rawIds.isEmpty()) return@withContext false
        val inList = rawIds.joinToString(",")
        val main = rawIds.first()
        val resolver = context.contentResolver
        val ops = arrayListOf<ContentProviderOperation>()

        // Nombre: se escribe el nombre completo y se borran las partes, que Android
        // vuelve a calcular a partir de él.
        val hasName = resolver.query(
            Data.CONTENT_URI, arrayOf(Data._ID),
            "${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($inList)",
            arrayOf(StructuredName.CONTENT_ITEM_TYPE), null
        )?.use { it.count > 0 } ?: false
        if (hasName) {
            ops += ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                .withSelection("${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($inList)", arrayOf(StructuredName.CONTENT_ITEM_TYPE))
                .withValue(StructuredName.DISPLAY_NAME, name.trim())
                .withValue(StructuredName.GIVEN_NAME, null)
                .withValue(StructuredName.MIDDLE_NAME, null)
                .withValue(StructuredName.FAMILY_NAME, null)
                .withValue(StructuredName.PREFIX, null)
                .withValue(StructuredName.SUFFIX, null)
                .build()
        } else {
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                .withValue(Data.RAW_CONTACT_ID, main)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.DISPLAY_NAME, name.trim())
                .build()
        }

        // Teléfono: se cambia el que se ve en LlamaditApp; si no aparece, se añade.
        if (phone.trim() != oldPhone.trim()) {
            val phoneRow = resolver.query(
                Data.CONTENT_URI, arrayOf(Data._ID),
                "${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($inList) AND ${Phone.NUMBER} = ?",
                arrayOf(Phone.CONTENT_ITEM_TYPE, oldPhone), null
            )?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
            ops += if (phoneRow != null) {
                ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, phoneRow))
                    .withValue(Phone.NUMBER, phone.trim())
                    .withValue(Phone.NORMALIZED_NUMBER, null)
                    .build()
            } else {
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, main)
                    .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    .withValue(Phone.NUMBER, phone.trim())
                    .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                    .withValue(Data.IS_PRIMARY, 1)
                    .withValue(Data.IS_SUPER_PRIMARY, 1)
                    .build()
            }
        }

        if (photoChange != PhotoChange.KEEP) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($inList)", arrayOf(Photo.CONTENT_ITEM_TYPE))
                .build()
            if (photoChange == PhotoChange.REPLACE && photo != null) {
                ops += ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, main)
                    .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                    .withValue(Data.IS_SUPER_PRIMARY, 1)
                    .withValue(Photo.PHOTO, photo.toJpeg())
                    .build()
            }
        }
        resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        true
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo cambiar el contacto", e)
        false
    }
}

/** Contactos con teléfono que aún no son favoritos, para añadirlos desde la agenda. */
suspend fun loadOtherContacts(context: Context): List<Contact> = withContext(Dispatchers.IO) {
    val byContact = linkedMapOf<Long, Contact>()
    try {
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER),
            "${Phone.STARRED} = 0",
            null,
            "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val number = c.getString(2) ?: continue
                byContact.getOrPut(id) { Contact(id, c.getString(1).orEmpty(), number) }
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso de contactos", e)
    }
    byContact.values.toList()
}
