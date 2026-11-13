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

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.Base64
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.encodeHomeLayout
import com.launcherfacil.app.contacts.contactsWithWhatsApp
import com.launcherfacil.app.contacts.favoritesWithoutPhoto
import com.launcherfacil.app.contacts.isWhatsAppInstalled
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.loadGoogleAccounts
import com.launcherfacil.app.exercises.Exercise
import com.launcherfacil.app.exercises.ExerciseProgress
import com.launcherfacil.app.gohome.hasPortalPhoto
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.pills.PillStore
import com.launcherfacil.app.radio.RadioStations
import com.launcherfacil.app.ui.toBatteryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/** Lado de las fotos que se suben para la web (pequeñas: caben en el documento). */
private const val PHOTO_SIDE = 256
private const val PILL_PHOTO_SIDE = 480

/**
 * Copia del estado del móvil para la web del cuidador: lo mismo que se ve en el panel.
 * Las fotos van aparte y solo se suben si han cambiado.
 */
internal object RemoteState {

    suspend fun publish(context: Context, device: DocumentReference, name: String) {
        val settings = CaregiverSettings(context)
        val favorites = loadFavoriteContacts(context)
        val noPhoto = favoritesWithoutPhoto(context)
        val whatsApp = contactsWithWhatsApp(context)
        val sos = (favorites.firstOrNull { it.id == settings.sosContactId } ?: favorites.firstOrNull())?.id
        val locate = settings.locateContactIds
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.toBatteryState()
        val progress = ExerciseProgress(context)
        val radio = RadioStations(context)
        val pills = PillStore(context)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()

        val state = mapOf(
            "name" to name,
            "appVersion" to version,
            "lastSeen" to FieldValue.serverTimestamp(),
            "battery" to battery?.let { mapOf("percent" to it.percent, "charging" to it.charging) },
            "kiosk" to when {
                !Kiosk.isDeviceOwner(context) -> "off"
                Kiosk.inCaregiverSession(context) -> "session"
                settings.kioskPaused -> "paused"
                else -> "locked"
            },
            "people" to favorites.map { p ->
                mapOf(
                    "id" to p.id,
                    "name" to p.name,
                    "phone" to p.phone,
                    "photo" to (p.id !in noPhoto),
                    "sos" to (p.id == sos),
                    "whatsapp" to (p.id in whatsApp),
                    "locate" to (locate?.contains(p.id) ?: true)
                )
            },
            "googleAccounts" to loadGoogleAccounts(context).map { it.name },
            "homeLayout" to encodeHomeLayout(settings.homeLayout),
            "backButton" to settings.backButton.name,
            "sosIcon" to settings.sosIcon.name,
            "keywords" to settings.keywords,
            "wifi" to settings.wifiEnabled,
            "ringing" to FindPhone.isRinging,
            "goHome" to mapOf(
                "address" to settings.houseAddress,
                "hasHouse" to (settings.housePosition != null),
                "portalPhoto" to hasPortalPhoto(context),
                "alert" to settings.goHomeAlert,
                "updates" to settings.goHomeUpdates
            ),
            "exercises" to Exercise.entries.map { e ->
                mapOf(
                    "id" to e.name,
                    "enabled" to progress.isEnabled(e),
                    "level" to progress.level(e),
                    "played" to progress.played(e),
                    "wellDone" to progress.wellDone(e)
                )
            },
            "radio" to mapOf(
                "stations" to radio.stations.map { mapOf("uuid" to it.uuid, "name" to it.name) },
                "sleepMinutes" to radio.sleepMinutes
            ),
            "lowBattery" to mapOf("on" to settings.lowBatteryAlert, "percent" to settings.lowBatteryPercent),
            "pills" to mapOf(
                "enabled" to pills.enabled,
                "doses" to pills.doses.map {
                    mapOf("id" to it.id, "minutes" to it.minutes, "count" to it.count.toDouble(), "photo" to pills.hasPhoto(it.id))
                },
                // Historial de tomas (30 días): para ver desde lejos si se las está tomando.
                "history" to pills.history().map { r ->
                    mapOf(
                        "date" to r.date.toString(), "minutes" to r.minutes, "status" to r.status.name.lowercase(), "at" to r.at,
                        "skipped" to r.skipped, "total" to r.total,
                        "boxes" to r.boxes.map { b -> mapOf("id" to b.id, "count" to b.count.toDouble(), "taken" to b.taken) }
                    )
                }
            ),
            "news" to settings.newsPapers,
            "whatsappInstalled" to context.isWhatsAppInstalled(),
            "notificationAccess" to NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName),
        )
        device.set(mapOf("state" to state), SetOptions.merge()).await()
        uploadPhotos(context, device, favorites.map { it.id }.filter { it !in noPhoto })
        uploadPillPhotos(context, device, pills)
    }

    /**
     * Fotos de las cajas de pastillas (documentos "pill_<id>" de la misma colección de
     * fotos, así no hay que cambiar las reglas de Firebase). Se borran las que ya no están.
     */
    private suspend fun uploadPillPhotos(context: Context, device: DocumentReference, pills: PillStore) {
        val prefs = context.getSharedPreferences("remote_photos", Context.MODE_PRIVATE)
        // Las de ahora y las que salen en el historial (aunque ya se hayan quitado).
        val doses = pills.photoIds()
        for (key in prefs.all.keys.filter { it.startsWith("pill_") && it.removePrefix("pill_") !in doses }) {
            device.collection("photos").document(key).delete().await()
            prefs.edit { remove(key) }
        }
        for (id in doses) {
            val jpeg = withContext(Dispatchers.IO) {
                val bitmap = pills.loadPhoto(id) ?: return@withContext null
                val scale = PILL_PHOTO_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height)
                val small = if (scale < 1) Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true) else bitmap
                ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
            } ?: continue
            val key = "pill_$id"
            val hash = jpeg.contentHashCode().toString()
            if (prefs.getString(key, null) == hash) continue
            device.collection("photos").document(key)
                .set(mapOf("jpeg" to Base64.encodeToString(jpeg, Base64.NO_WRAP), "at" to FieldValue.serverTimestamp()))
                .await()
            prefs.edit { putString(key, hash) }
        }
    }

    /** Sube la foto de cada persona si ha cambiado desde la última vez (se guarda una huella). */
    private suspend fun uploadPhotos(context: Context, device: DocumentReference, ids: List<Long>) {
        val prefs = context.getSharedPreferences("remote_photos", Context.MODE_PRIVATE)
        for (id in ids) {
            val jpeg = smallPhoto(context, id) ?: continue
            val hash = jpeg.contentHashCode().toString()
            if (prefs.getString(id.toString(), null) == hash) continue
            device.collection("photos").document(id.toString())
                .set(mapOf("jpeg" to Base64.encodeToString(jpeg, Base64.NO_WRAP), "at" to FieldValue.serverTimestamp()))
                .await()
            prefs.edit { putString(id.toString(), hash) }
        }
    }

    private suspend fun smallPhoto(context: Context, contactId: Long): ByteArray? = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        val bitmap = ContactsContract.Contacts.openContactPhotoInputStream(context.contentResolver, uri, true)
            ?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
        val side = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val small = if (side > PHOTO_SIDE) Bitmap.createScaledBitmap(square, PHOTO_SIDE, PHOTO_SIDE, true) else square
        ByteArrayOutputStream().also { small.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
    }

    /** Olvida las huellas de las fotos (al vincular de nuevo se vuelven a subir todas). */
    fun forgetPhotos(context: Context) =
        context.getSharedPreferences("remote_photos", Context.MODE_PRIVATE).edit { clear() }
}
