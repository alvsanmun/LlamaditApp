package com.launcherfacil.app.locate

import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.sos.mapsLink
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

// Palabras clave aceptadas (se comparan sin tildes, mayúsculas ni signos).
// Se podrán cambiar en la configuración del cuidador (paso 7).
private val KEYWORDS = setOf("UBICACION", "DONDE ESTAS")

/** ¿El texto del SMS es una petición de ubicación? */
fun isLocateRequest(body: String): Boolean {
    val normalized = Normalizer.normalize(body, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")          // quita tildes
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9 ]"), " ")        // quita signos (¿?¡!.)
        .replace(Regex("\\s+"), " ")
        .trim()
    return normalized in KEYWORDS
}

/** ¿El remitente está autorizado? Por ahora: cualquier contacto favorito (★). */
suspend fun isAuthorizedSender(context: Context, sender: String): Boolean =
    loadFavoriteContacts(context).any { samePhoneNumber(context, it.phone, sender) }

/**
 * Compara números aunque uno lleve prefijo (+34) y el otro no. Además de la
 * comparación de Android (que depende del país de la red), acepta que coincidan
 * las 9 últimas cifras: los móviles españoles tienen 9.
 */
private fun samePhoneNumber(context: Context, a: String, b: String): Boolean {
    val da = a.filter { it.isDigit() }
    val db = b.filter { it.isDigit() }
    if (da.length >= 9 && db.length >= 9 && da.takeLast(9) == db.takeLast(9)) return true
    return androidSamePhoneNumber(context, a, b)
}

private fun androidSamePhoneNumber(context: Context, a: String, b: String): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val country = context.getSystemService(TelephonyManager::class.java)
            ?.networkCountryIso?.takeIf { it.isNotEmpty() }
            ?: Locale.getDefault().country.lowercase(Locale.ROOT)
        PhoneNumberUtils.areSamePhoneNumber(a, b, country)
    } else {
        @Suppress("DEPRECATION")
        PhoneNumberUtils.compare(context, a, b)
    }

fun locateReply(location: Location?): String =
    if (location != null) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(location.time))
        "Ubicación: ${location.mapsLink()} (±${location.accuracy.toInt()} m, $time)"
    } else {
        "No se ha podido obtener la ubicación ahora mismo."
    }
