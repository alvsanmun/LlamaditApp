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

package com.launcherfacil.app.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.launcherfacil.app.config.BackButton
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.SosIcon
import com.launcherfacil.app.config.decodeHomeLayout
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.PhotoChange
import com.launcherfacil.app.contacts.contactsWithWhatsApp
import com.launcherfacil.app.contacts.createPerson
import com.launcherfacil.app.contacts.isValidPhone
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.loadGoogleAccounts
import com.launcherfacil.app.contacts.setHasWhatsApp
import com.launcherfacil.app.contacts.setStarred
import com.launcherfacil.app.contacts.updatePerson
import com.launcherfacil.app.exercises.Exercise
import com.launcherfacil.app.exercises.ExerciseProgress
import com.launcherfacil.app.exercises.MAX_LEVEL
import com.launcherfacil.app.gohome.positionOf
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.locate.LocateService
import com.launcherfacil.app.radio.RadioStations
import com.launcherfacil.app.radio.SLEEP_OPTIONS
import com.launcherfacil.app.ui.toBatteryState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Órdenes que llegan de la web del cuidador (`devices/{uid}/commands`). Cada una lleva
 * `type`, sus datos en `args`, quién la manda (`by`) y `status` = "pending". Al
 * terminar se pone "done" (con `result` si hace falta) o "error" (con `error`).
 * Se ejecutan de una en una.
 */
internal object RemoteCommands {
    private const val TAG = "RemoteCommands"
    private val mutex = Mutex()
    private val running = mutableSetOf<String>()

    /** Sube cada vez que una orden cambia la configuración, para que las pantallas la relean. */
    private val _settingsVersion = MutableStateFlow(0)
    val settingsVersion: StateFlow<Int> = _settingsVersion

    suspend fun run(context: Context, doc: DocumentSnapshot) {
        if (!running.add(doc.id)) return
        mutex.withLock {
            val type = doc.getString("type").orEmpty()
            @Suppress("UNCHECKED_CAST")
            val args = (doc.get("args") as? Map<String, Any?>).orEmpty()
            Log.i(TAG, "Orden $type ${args.keys}")
            try {
                // La ubicación tarda: la termina LocateService.
                if (type == "locate") {
                    ContextCompat.startForegroundService(context, LocateService.remoteIntent(context, doc.reference.path))
                    return@withLock
                }
                val result = execute(context, type, args)
                finish(doc.reference.path, result = result)
                _settingsVersion.value++
                Remote.publishSoon(context)
            } catch (e: Exception) {
                Log.w(TAG, "La orden $type ha fallado", e)
                finish(doc.reference.path, error = e.message ?: e.javaClass.simpleName)
            } finally {
                running.remove(doc.id)
            }
        }
    }

    /** Marca la orden como hecha (o fallida). */
    fun finish(path: String, result: Map<String, Any?>? = null, error: String? = null) {
        val data = mutableMapOf<String, Any?>(
            "status" to if (error == null) "done" else "error",
            "doneAt" to FieldValue.serverTimestamp()
        )
        result?.let { data["result"] = it }
        error?.let { data["error"] = it }
        Remote.db().document(path).update(data)
    }

    /** Resultado de "¿Dónde está?": posición y batería. */
    fun locationResult(context: Context, location: Location?): Map<String, Any?> {
        val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?.toBatteryState()
        return mapOf(
            "found" to (location != null),
            "lat" to location?.latitude,
            "lng" to location?.longitude,
            "accuracy" to location?.accuracy?.toDouble(),
            "time" to location?.time,
            "battery" to battery?.percent,
            "charging" to battery?.charging
        )
    }

    private fun Map<String, Any?>.long(key: String): Long =
        (this[key] as? Number)?.toLong() ?: (this[key] as? String)?.toLongOrNull() ?: error("Falta $key")

    private fun Map<String, Any?>.bool(key: String): Boolean = this[key] as? Boolean ?: error("Falta $key")

    private fun Map<String, Any?>.string(key: String): String = this[key] as? String ?: error("Falta $key")

    private fun decodePhoto(base64: String?): Bitmap? = base64?.takeIf { it.isNotBlank() }?.let {
        val bytes = Base64.decode(it.substringAfter(","), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private suspend fun person(context: Context, id: Long): Contact =
        loadFavoriteContacts(context).firstOrNull { it.id == id } ?: error("No está en la lista")

    private suspend fun execute(context: Context, type: String, args: Map<String, Any?>): Map<String, Any?>? {
        val settings = CaregiverSettings(context)
        when (type) {
            "ring" -> FindPhone.ring(context)
            "stopRing" -> FindPhone.stop(context)

            // --- Personas ---
            "addPerson" -> {
                val name = args.string("name").trim()
                val phone = args.string("phone").trim()
                require(name.isNotEmpty() && isValidPhone(phone)) { "Nombre o teléfono no válidos" }
                // Por defecto, en la primera cuenta de Google (como el formulario del panel).
                val account = if (args.containsKey("account")) args["account"] as String?
                else loadGoogleAccounts(context).firstOrNull()?.name
                val id = createPerson(context, name, phone, decodePhoto(args["photo"] as? String), account)
                    ?: error("No se pudo crear el contacto")
                if (args["whatsapp"] == true) setHasWhatsApp(context, Contact(id, name, phone), true)
                return mapOf("id" to id)
            }
            "updatePerson" -> {
                val old = person(context, args.long("id"))
                val name = (args["name"] as? String)?.trim() ?: old.name
                val phone = (args["phone"] as? String)?.trim() ?: old.phone
                require(name.isNotEmpty() && isValidPhone(phone)) { "Nombre o teléfono no válidos" }
                val photo = decodePhoto(args["photo"] as? String)
                val change = when {
                    photo != null -> PhotoChange.REPLACE
                    args["removePhoto"] == true -> PhotoChange.REMOVE
                    else -> PhotoChange.KEEP
                }
                check(updatePerson(context, old.id, old.phone, name, phone, change, photo)) { "No se pudo cambiar el contacto" }
                if (phone != old.phone && old.id in contactsWithWhatsApp(context)) {
                    setHasWhatsApp(context, old, false)
                    setHasWhatsApp(context, Contact(old.id, name, phone), true)
                }
            }
            "removePerson" -> check(setStarred(context, args.long("id"), false)) { "No se pudo quitar" }
            "reorder" -> settings.contactOrder = (args["ids"] as List<*>).map { (it as Number).toLong() }
            "setSos" -> settings.sosContactId = person(context, args.long("id")).id
            "setWhatsApp" -> check(setHasWhatsApp(context, person(context, args.long("id")), args.bool("on"))) {
                "No se pudo cambiar (¿teléfono sin prefijo?)"
            }
            "setLocate" -> {
                val id = args.long("id")
                val base = settings.locateContactIds ?: loadFavoriteContacts(context).map { it.id }.toSet()
                settings.locateContactIds = if (args.bool("on")) base + id else base - id
            }
            "setKeywords" -> {
                val words = (args["keywords"] as List<*>).mapNotNull { (it as? String)?.trim() }.filter { it.isNotEmpty() }
                require(words.isNotEmpty()) { "Hace falta al menos una palabra" }
                settings.keywords = words
            }

            // --- Pantalla principal ---
            "setHomeLayout" -> {
                settings.homeLayout = decodeHomeLayout(args.string("layout"))
                Kiosk.applyMaps(context)
            }
            "setBackButton" -> settings.backButton = BackButton.valueOf(args.string("value"))
            "setSosIcon" -> settings.sosIcon = SosIcon.valueOf(args.string("value"))

            // --- Volver a casa ---
            "setHouse" -> {
                val address = args.string("address").trim()
                val position = positionOf(context, address) ?: error("No se encuentra esa dirección")
                settings.housePosition = position
                settings.houseAddress = address
                Kiosk.applyMaps(context)
                return mapOf("lat" to position.first, "lng" to position.second)
            }
            "setGoHome" -> {
                (args["alert"] as? Boolean)?.let { settings.goHomeAlert = it }
                (args["updates"] as? Boolean)?.let { settings.goHomeUpdates = it }
            }

            // --- Ejercicios, radio y conexión ---
            "setExercise" -> {
                val exercise = Exercise.valueOf(args.string("id"))
                val progress = ExerciseProgress(context)
                (args["enabled"] as? Boolean)?.let { progress.setEnabled(exercise, it) }
                (args["level"] as? Number)?.let { progress.setLevel(exercise, it.toInt().coerceIn(1, MAX_LEVEL)) }
            }
            "removeStation" -> RadioStations(context).remove(args.string("uuid"))
            "setRadioSleep" -> {
                val minutes = (args["minutes"] as Number).toInt()
                require(minutes in SLEEP_OPTIONS) { "Tiempo no válido" }
                RadioStations(context).sleepMinutes = minutes
            }
            "setWifi" -> {
                settings.wifiEnabled = args.bool("on")
                Kiosk.applyWifi(context)
            }
            "setName" -> Remote.setDeviceName(context, args.string("name"))
            else -> error("Orden desconocida: $type")
        }
        return null
    }
}
