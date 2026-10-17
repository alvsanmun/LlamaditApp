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

package com.launcherfacil.app.locate

import android.content.Context
import android.location.Location
import android.os.Build
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.locateContacts
import com.launcherfacil.app.sos.mapsLink
import java.text.DateFormat
import java.text.Normalizer
import java.util.Date
import java.util.Locale

/** Texto sin tildes, mayúsculas ni signos (¿?¡!.), para comparar palabras clave. */
private fun normalize(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")          // quita tildes
        .uppercase(Locale.ROOT)
        .replace(Regex("[^A-Z0-9 ]"), " ")        // quita signos
        .replace(Regex("\\s+"), " ")
        .trim()

/** ¿El texto del SMS es una de las palabras clave que ha configurado el cuidador? */
fun isLocateRequest(context: Context, body: String): Boolean =
    normalize(body) in CaregiverSettings(context).keywords.map(::normalize)

/** ¿El remitente puede pedir la ubicación? (Por defecto, cualquier favorito.) */
suspend fun isAuthorizedSender(context: Context, sender: String): Boolean =
    locateContacts(context).any { samePhoneNumber(context, it.phone, sender) }

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
