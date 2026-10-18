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

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.Data
import android.util.Log
import com.launcherfacil.app.R
import com.launcherfacil.app.config.inCaregiverOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

const val WHATSAPP_PACKAGE = "com.whatsapp"

// Fila que WhatsApp añade a la agenda para la videollamada. Solo se usa WhatsApp
// para videollamadas; las llamadas de voz van por teléfono normal.
private const val MIME_VIDEO = "vnd.android.cursor.item/vnd.com.whatsapp.video.call"

data class WhatsAppContact(val contactId: Long, val name: String, val videoDataId: Long)

fun Context.isWhatsAppInstalled(): Boolean = try {
    packageManager.getPackageInfo(WHATSAPP_PACKAGE, 0)
    true
} catch (e: PackageManager.NameNotFoundException) {
    false
}

/** Contactos favoritos (★) que tienen WhatsApp, con su fila de videollamada. */
suspend fun loadFavoriteWhatsAppContacts(context: Context): List<WhatsAppContact> =
    withContext(Dispatchers.IO) {
        val byContact = linkedMapOf<Long, WhatsAppContact>()
        context.contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data._ID, Data.CONTACT_ID, Data.DISPLAY_NAME_PRIMARY),
            "${Data.STARRED} = 1 AND ${Data.MIMETYPE} = ?",
            arrayOf(MIME_VIDEO),
            "${Data.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC"
        )?.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getLong(1)
                byContact.getOrPut(contactId) {
                    WhatsAppContact(contactId, c.getString(2).orEmpty(), c.getLong(0))
                }
            }
        }
        byContact.values.toList().inCaregiverOrder(context) { it.contactId }
    }

/** Abre la fila de videollamada con WhatsApp, que la inicia directamente. */
fun Context.whatsAppVideoCall(contact: WhatsAppContact): Boolean {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse("content://com.android.contacts/data/${contact.videoDataId}"), MIME_VIDEO)
        .setPackage(WHATSAPP_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("WhatsApp", "WhatsApp no puede abrir la videollamada", e)
        false
    }
}

// --- Marcar a mano quién tiene WhatsApp ---
//
// WhatsApp 2.26 ya no escribe en la agenda sus filas de videollamada (visto en el A16
// el 2026-10-06), pero sigue abriendo la videollamada al recibir una. Así que la fila la
// escribe el launcher cuando el cuidador marca "Tiene WhatsApp" en un favorito.

/** Identificador de WhatsApp de un número: solo cifras con prefijo de país + "@s.whatsapp.net". */
fun whatsAppJid(phone: String): String? {
    var digits = phone.filter { it.isDigit() }
    if (digits.startsWith("00")) digits = digits.drop(2)
    // Números españoles sin prefijo (9 cifras): se añade el 34.
    if (digits.length == 9 && !phone.trim().startsWith("+")) digits = "34$digits"
    return if (digits.length >= 10) "$digits@s.whatsapp.net" else null
}

/** Ids de los contactos que tienen fila de videollamada de WhatsApp. */
suspend fun contactsWithWhatsApp(context: Context): Set<Long> = withContext(Dispatchers.IO) {
    val ids = mutableSetOf<Long>()
    context.contentResolver.query(
        Data.CONTENT_URI, arrayOf(Data.CONTACT_ID), "${Data.MIMETYPE} = ?", arrayOf(MIME_VIDEO), null
    )?.use { c -> while (c.moveToNext()) ids += c.getLong(0) }
    ids
}

/** Añade o quita la fila de videollamada de WhatsApp del contacto. Devuelve si lo consiguió. */
suspend fun setHasWhatsApp(context: Context, contact: Contact, has: Boolean): Boolean =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // Contactos "en bruto" del contacto que no son de WhatsApp (los del teléfono o Google).
        val rawIds = mutableListOf<Long>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0 AND " +
                "(${ContactsContract.RawContacts.ACCOUNT_TYPE} IS NULL OR ${ContactsContract.RawContacts.ACCOUNT_TYPE} != ?)",
            arrayOf(contact.id.toString(), WHATSAPP_PACKAGE), null
        )?.use { c -> while (c.moveToNext()) rawIds += c.getLong(0) }
        if (rawIds.isEmpty()) return@withContext false

        try {
            val inList = rawIds.joinToString(",")
            if (has) {
                if (contact.id in contactsWithWhatsApp(context)) return@withContext true
                val jid = whatsAppJid(contact.phone) ?: return@withContext false
                val values = ContentValues().apply {
                    put(Data.RAW_CONTACT_ID, rawIds.first())
                    put(Data.MIMETYPE, MIME_VIDEO)
                    put(Data.DATA1, jid)
                    put(Data.DATA2, "WhatsApp")
                    put(Data.DATA3, context.getString(R.string.whatsapp_video_row))
                }
                resolver.insert(Data.CONTENT_URI, values) != null
            } else {
                resolver.delete(
                    Data.CONTENT_URI,
                    "${Data.MIMETYPE} = ? AND ${Data.RAW_CONTACT_ID} IN ($inList)",
                    arrayOf(MIME_VIDEO)
                )
                true
            }
        } catch (e: SecurityException) {
            Log.w("WhatsApp", "Sin permiso para escribir en la agenda", e)
            false
        }
    }
