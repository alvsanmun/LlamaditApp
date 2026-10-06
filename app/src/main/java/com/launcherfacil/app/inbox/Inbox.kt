package com.launcherfacil.app.inbox

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog
import android.provider.Telephony
import android.util.Log
import androidx.core.content.edit
import com.launcherfacil.app.locate.isLocateRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SmsMessage(val id: Long, val address: String, val body: String, val date: Long)

data class MissedCall(val id: Long, val number: String, val date: Long)

private const val TAG = "Inbox"
private const val PREFS = "inbox"
private const val KEY_HIDDEN_SMS = "hidden_sms_ids"
private const val KEY_SMS_SEEN_AT = "sms_seen_at"
private const val MAX_ITEMS = 200

private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

// --- SMS ---

/**
 * SMS recibidos, del más nuevo al más antiguo. Se quitan los que la persona ha
 * "borrado" y las peticiones de ubicación de los cuidadores.
 */
suspend fun loadInboxSms(context: Context): List<SmsMessage> = withContext(Dispatchers.IO) {
    val hidden = context.prefs().getStringSet(KEY_HIDDEN_SMS, emptySet()).orEmpty()
    val result = mutableListOf<SmsMessage>()
    try {
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            null, null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && result.size < MAX_ITEMS) {
                val id = c.getLong(0)
                val body = c.getString(2).orEmpty()
                if (id.toString() in hidden || isLocateRequest(context, body)) continue
                result += SmsMessage(id, c.getString(1).orEmpty(), body, c.getLong(3))
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso para leer los SMS", e)
    }
    result
}

/**
 * "Borra" un SMS de la lista del launcher. Solo la app de SMS predeterminada (Google
 * Mensajes) puede borrarlo del teléfono, así que se recuerda como oculto.
 */
fun hideSms(context: Context, id: Long) {
    val prefs = context.prefs()
    val hidden = prefs.getStringSet(KEY_HIDDEN_SMS, emptySet()).orEmpty() + id.toString()
    prefs.edit { putStringSet(KEY_HIDDEN_SMS, hidden) }
}

/** SMS nuevos desde la última vez que se abrió la lista (para el número del botón). */
suspend fun countNewSms(context: Context): Int {
    val seenAt = context.prefs().getLong(KEY_SMS_SEEN_AT, 0)
    return loadInboxSms(context).count { it.date > seenAt }
}

fun markSmsSeen(context: Context) {
    context.prefs().edit { putLong(KEY_SMS_SEEN_AT, System.currentTimeMillis()) }
}

// --- Llamadas perdidas ---

private const val MISSED = "${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}"

suspend fun loadMissedCalls(context: Context): List<MissedCall> = withContext(Dispatchers.IO) {
    val result = mutableListOf<MissedCall>()
    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.DATE),
            MISSED, null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { c ->
            while (c.moveToNext() && result.size < MAX_ITEMS) {
                result += MissedCall(c.getLong(0), c.getString(1).orEmpty(), c.getLong(2))
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso para leer el registro de llamadas", e)
    }
    result
}

/** Llamadas perdidas que aún no se han visto (las que marca el número del botón). */
suspend fun countNewMissedCalls(context: Context): Int = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, arrayOf(CallLog.Calls._ID),
            "$MISSED AND ${CallLog.Calls.NEW} = 1", null, null
        )?.use { it.count } ?: 0
    } catch (e: SecurityException) {
        0
    }
}

/** Marca las perdidas como vistas (somos la app de Teléfono: nos toca a nosotros). */
suspend fun markMissedCallsSeen(context: Context) = withContext(Dispatchers.IO) {
    try {
        val values = ContentValues().apply {
            put(CallLog.Calls.NEW, 0)
            put(CallLog.Calls.IS_READ, 1)
        }
        context.contentResolver.update(
            CallLog.Calls.CONTENT_URI, values, "$MISSED AND ${CallLog.Calls.NEW} = 1", null
        )
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso para marcar las llamadas perdidas", e)
    }
}
