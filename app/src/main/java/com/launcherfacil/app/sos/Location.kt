package com.launcherfacil.app.sos

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

private fun Context.has(permission: String) =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/**
 * Una posición puntual (nada de seguimiento continuo). Prefiere el GPS; si no
 * responde a tiempo usa la red, y como último recurso la última posición conocida.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context, gpsTimeoutMs: Long = 20_000): Location? {
    val fine = context.has(Manifest.permission.ACCESS_FINE_LOCATION)
    val coarse = fine || context.has(Manifest.permission.ACCESS_COARSE_LOCATION)
    if (!coarse) return null

    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = buildList {
        if (fine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
    }

    val fresh = coroutineScope {
        val gps = async {
            if (LocationManager.GPS_PROVIDER in providers) lm.current(context, LocationManager.GPS_PROVIDER) else null
        }
        val net = async {
            if (LocationManager.NETWORK_PROVIDER in providers) lm.current(context, LocationManager.NETWORK_PROVIDER) else null
        }
        val best = withTimeoutOrNull(gpsTimeoutMs) { gps.await() }
            ?: withTimeoutOrNull(3_000) { net.await() }
        gps.cancel()
        net.cancel()
        best
    }
    return fresh ?: providers.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
}

@SuppressLint("MissingPermission")
private suspend fun LocationManager.current(context: Context, provider: String): Location? =
    suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        LocationManagerCompat.getCurrentLocation(
            this, provider, signal, ContextCompat.getMainExecutor(context)
        ) { location -> if (cont.isActive) cont.resume(location) }
    }

fun Location.mapsLink(): String =
    String.format(Locale.US, "https://maps.google.com/?q=%.6f,%.6f", latitude, longitude)
