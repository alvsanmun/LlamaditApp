package com.launcherfacil.app.contacts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.Data
import android.util.Log
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
        byContact.values.toList()
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
