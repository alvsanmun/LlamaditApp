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

package com.launcherfacil.app.gohome

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "GoHome"
const val MAPS_PACKAGE = "com.google.android.apps.maps"

/** Volumen de la voz de la ruta (el de multimedia, que la app deja a cero). */
private const val GUIDANCE_VOLUME = 0.8f

/**
 * Abre la navegación a pie de Google Maps hacia casa, con voz. Antes sube el volumen
 * de multimedia (la voz de Maps lo usa); vuelve a cero al regresar al launcher.
 */
fun Activity.startWalkingRoute(lat: Double, lng: Double): Boolean {
    getSystemService(AudioManager::class.java)?.let { am ->
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * GUIDANCE_VOLUME).toInt().coerceAtLeast(1), 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "No se pudo subir el volumen de la ruta", e)
        }
    }
    val navigation = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
        .setPackage(MAPS_PACKAGE)
    // Sin Google Maps: la ruta a pie en la web o en otra app de mapas.
    val web = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
    )
    for (intent in listOf(navigation, web)) {
        try {
            startActivity(intent)
            return true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No se pudo abrir $intent", e)
        }
    }
    return false
}

/** Para que el cuidador compruebe el punto: Google Maps con una chincheta en la casa. */
fun housePinIntent(lat: Double, lng: Double): Intent =
    Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng")).setPackage(MAPS_PACKAGE)

/** Dirección de unas coordenadas ("Calle Mayor, 12, 41001 Sevilla"), o null si no se sabe. */
suspend fun addressOf(context: Context, lat: Double, lng: Double): String? =
    geocode(context) { geocoder, done ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = done(addresses.firstOrNull())
                override fun onError(errorMessage: String?) = done(null)
            })
        } else {
            @Suppress("DEPRECATION")
            done(geocoder.getFromLocation(lat, lng, 1)?.firstOrNull())
        }
    }?.let { address ->
        (0..address.maxAddressLineIndex).joinToString(", ") { address.getAddressLine(it) }
    }

/** Coordenadas de una dirección escrita, o null si no se encuentra. */
suspend fun positionOf(context: Context, text: String): Pair<Double, Double>? =
    geocode(context) { geocoder, done ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocationName(text, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = done(addresses.firstOrNull())
                override fun onError(errorMessage: String?) = done(null)
            })
        } else {
            @Suppress("DEPRECATION")
            done(geocoder.getFromLocationName(text, 1)?.firstOrNull())
        }
    }?.let { it.latitude to it.longitude }

/** El Geocoder de Android (en móviles con Google, el de Google Maps). Necesita internet. */
private suspend fun geocode(
    context: Context,
    query: (Geocoder, (Address?) -> Unit) -> Unit
): Address? = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext null
    val geocoder = Geocoder(context, Locale.getDefault())
    try {
        suspendCancellableCoroutine { cont ->
            query(geocoder) { if (cont.isActive) cont.resume(it) }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Geocoder sin servicio", e)
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}
