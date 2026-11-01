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

package com.launcherfacil.app.radio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Directorio de emisoras radio-browser.info: libre, gratuito y sin registro. Lo
 * mantiene una comunidad de voluntarios en varios servidores; [all.api…] dice cuáles
 * hay. Solo se usa desde el panel del cuidador (buscar y añadir emisoras) y, al poner
 * una emisora, para contar la escucha y saber si su dirección ha cambiado.
 */
object RadioBrowser {
    private const val TAG = "RadioBrowser"
    private const val SERVERS_URL = "https://all.api.radio-browser.info/json/servers"
    private val FALLBACK_SERVERS = listOf("de1.api.radio-browser.info", "de2.api.radio-browser.info", "fi1.api.radio-browser.info")
    private const val LIMIT = 40

    private const val USER_AGENT = "LlamaditApp (https://github.com/alvsanmun/LlamaditApp)"
    private var server: String? = null

    /** Las más escuchadas del país del móvil (el de la SIM o, si no hay, el del idioma). */
    suspend fun top(context: Context): List<Station>? =
        search("countrycode=${countryCode(context)}&order=clickcount&reverse=true&hidebroken=true&limit=$LIMIT")

    /** Búsqueda por nombre en todo el mundo, primero las más escuchadas. */
    suspend fun byName(name: String): List<Station>? =
        search("name=${URLEncoder.encode(name.trim(), "UTF-8")}&order=clickcount&reverse=true&hidebroken=true&limit=$LIMIT")

    /**
     * Avisa de que se escucha la emisora (lo pide el directorio para ordenar las más
     * escuchadas) y devuelve su dirección actual, por si ha cambiado. Null si no hay red.
     */
    suspend fun resolve(uuid: String): String? = withContext(Dispatchers.IO) {
        val text = get("/json/url/$uuid") ?: return@withContext null
        try {
            JSONObject(text).optString("url").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun search(query: String): List<Station>? = withContext(Dispatchers.IO) {
        val text = get("/json/stations/search?$query") ?: return@withContext null
        try {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val url = o.optString("url_resolved").ifBlank { o.optString("url") }
                if (url.isBlank()) return@mapNotNull null
                Station(o.getString("stationuuid"), o.optString("name").trim(), url, o.optString("favicon"))
            }.distinctBy { it.name.lowercase() }
        } catch (e: Exception) {
            Log.w(TAG, "Respuesta extraña del directorio", e)
            null
        }
    }

    /** Pide [path] al servidor conocido o, si falla, a los demás. */
    private fun get(path: String): String? {
        val candidates = listOfNotNull(server) + servers().filter { it != server }
        for (host in candidates) {
            val text = read("https://$host$path") ?: continue
            server = host
            return text
        }
        return null
    }

    private fun servers(): List<String> = try {
        val array = JSONArray(read(SERVERS_URL) ?: "[]")
        (0 until array.length()).map { array.getJSONObject(it).getString("name") }.distinct().shuffled()
            .ifEmpty { FALLBACK_SERVERS }
    } catch (e: Exception) {
        FALLBACK_SERVERS
    }

    private fun read(url: String): String? = try {
        open(url).run {
            try {
                if (responseCode == 200) inputStream.bufferedReader().use { it.readText() } else null
            } finally {
                disconnect()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer $url", e)
        null
    }

    internal fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
        }

    private fun countryCode(context: Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = tm?.simCountryIso?.takeIf { it.length == 2 }
            ?: tm?.networkCountryIso?.takeIf { it.length == 2 }
            ?: Locale.getDefault().country
        return iso.uppercase(Locale.ROOT).ifBlank { "ES" }
    }
}

/** Descarga una imagen (siguiendo redirecciones, también de http a https). */
internal fun fetchBitmap(url: String, redirects: Int = 4): Bitmap? = try {
    val connection = RadioBrowser.open(url).apply { instanceFollowRedirects = false }
    try {
        when (connection.responseCode) {
            in 300..399 -> {
                val next = connection.getHeaderField("Location")
                if (next != null && redirects > 0) fetchBitmap(URL(URL(url), next).toString(), redirects - 1) else null
            }
            200 -> connection.inputStream.use { BitmapFactory.decodeStream(it) }
            else -> null
        }
    } finally {
        connection.disconnect()
    }
} catch (e: Exception) {
    Log.w("RadioBrowser", "No se pudo descargar el logotipo $url", e)
    null
}
