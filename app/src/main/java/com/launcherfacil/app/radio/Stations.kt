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
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Una emisora. [uuid] es su identificador en radio-browser.info (sirve para volver a
 * pedir su dirección si deja de sonar); [favicon], la dirección de su logotipo.
 */
data class Station(val uuid: String, val name: String, val url: String, val favicon: String = "")

/** Minutos posibles para el apagado automático (0 = nunca). */
val SLEEP_OPTIONS = listOf(30, 60, 120, 180, 0)

/**
 * Emisoras elegidas por el cuidador (en su orden), apagado automático y volumen de la
 * radio. Los logotipos se descargan al añadir la emisora y se guardan en el móvil: así
 * la pantalla de la radio no tiene que pedirlos a internet cada vez.
 */
class RadioStations(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("radio", Context.MODE_PRIVATE)

    var stations: List<Station>
        get() = try {
            val array = JSONArray(prefs.getString(KEY_STATIONS, "[]"))
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Station(o.getString("uuid"), o.getString("name"), o.getString("url"), o.optString("favicon"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lista de emisoras dañada", e)
            emptyList()
        }
        set(value) = prefs.edit {
            val array = JSONArray()
            value.forEach {
                array.put(JSONObject().put("uuid", it.uuid).put("name", it.name).put("url", it.url).put("favicon", it.favicon))
            }
            putString(KEY_STATIONS, array.toString())
        }

    fun find(uuid: String): Station? = stations.firstOrNull { it.uuid == uuid }

    fun add(station: Station) {
        if (stations.none { it.uuid == station.uuid }) stations = stations + station
    }

    fun remove(uuid: String) {
        stations = stations.filterNot { it.uuid == uuid }
        logoFile(appContext, uuid).delete()
    }

    /** La dirección de una emisora ha cambiado (lo dice radio-browser.info al pedirla). */
    fun updateUrl(uuid: String, url: String) {
        stations = stations.map { if (it.uuid == uuid) it.copy(url = url) else it }
    }

    /** Minutos tras los que la radio se apaga sola (0 = nunca). Por defecto, una hora. */
    var sleepMinutes: Int
        get() = prefs.getInt(KEY_SLEEP, 60)
        set(value) = prefs.edit { putInt(KEY_SLEEP, value) }

    /** Último volumen de la radio (de multimedia); por defecto, un poco más de la mitad. */
    fun volume(am: AudioManager): Int {
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return prefs.getInt(KEY_VOLUME, (max * 0.6f).toInt()).coerceIn(1, max)
    }

    fun saveVolume(level: Int) = prefs.edit { putInt(KEY_VOLUME, level) }

    companion object {
        private const val TAG = "RadioStations"
        private const val KEY_STATIONS = "stations"
        private const val KEY_SLEEP = "sleep_minutes"
        private const val KEY_VOLUME = "volume"
    }
}

private const val LOGO_SIDE = 400

private fun logoFile(context: Context, uuid: String) =
    File(context.filesDir, "radio/${uuid.filter { it.isLetterOrDigit() || it == '-' }}.png")

suspend fun loadLogo(context: Context, uuid: String): Bitmap? = withContext(Dispatchers.IO) {
    val file = logoFile(context, uuid)
    if (file.exists()) BitmapFactory.decodeFile(file.path) else null
}

/** Descarga el logotipo de la emisora y lo guarda. Devuelve false si no tiene o no se puede leer. */
suspend fun downloadLogo(context: Context, station: Station): Boolean = withContext(Dispatchers.IO) {
    if (station.favicon.isBlank()) return@withContext false
    val bitmap = fetchBitmap(station.favicon) ?: return@withContext false
    saveLogo(context, station.uuid, bitmap)
}

/** El cuidador elige otro dibujo para la emisora (de la galería). */
suspend fun saveLogoFrom(context: Context, uuid: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    val bitmap = try {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    } catch (e: Exception) {
        Log.w("RadioStations", "No se pudo leer la imagen", e)
        null
    } ?: return@withContext false
    saveLogo(context, uuid, bitmap)
}

private fun saveLogo(context: Context, uuid: String, bitmap: Bitmap): Boolean = try {
    val side = maxOf(bitmap.width, bitmap.height)
    val scaled = if (side > LOGO_SIDE) {
        val scale = LOGO_SIDE.toFloat() / side
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    } else bitmap
    val file = logoFile(context, uuid).apply { parentFile?.mkdirs() }
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    true
} catch (e: Exception) {
    Log.w("RadioStations", "No se pudo guardar el logotipo", e)
    false
}
