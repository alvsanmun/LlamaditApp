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

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import com.launcherfacil.app.MainActivity
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.resolveSosContact
import com.launcherfacil.app.locate.locationDetail
import com.launcherfacil.app.sos.currentLocation
import com.launcherfacil.app.sos.sendSms
import com.launcherfacil.app.sos.startLocationForeground
import com.launcherfacil.app.sos.withBattery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Volver a casa", en segundo plano:
 *  - [alert]: avisa por SMS al contacto del SOS de que la persona quiere volver, con
 *    su ubicación (una sola vez).
 *  - [watch]: mientras dura la ruta de Google Maps, mira el GPS del móvil y, al llegar
 *    cerca de casa, abre la pantalla del portal. Maps no avisa a otras apps de que la
 *    ruta ha terminado. Se deja de mirar al llegar o, como mucho, a las [WATCH_LIMIT_MS].
 *    Si el cuidador lo activa, durante la ruta se le envía la ubicación por SMS cada
 *    [UPDATES_MS] y un último SMS al llegar; si no, la posición no se envía a nadie.
 */
class GoHomeService : Service() {

    companion object {
        private const val TAG = "GoHomeService"
        private const val NOTIFICATION_ID = 3
        private const val ACTION_ALERT = "alert"
        private const val ACTION_WATCH = "watch"
        private const val EXTRA_LAT = "lat"
        private const val EXTRA_LNG = "lng"
        private const val EXTRA_DEBUG_INTERVAL_SECONDS = "debug_interval_seconds"

        /** Distancia a la que se da por llegado (el GPS en la calle falla unos 10-20 m). */
        const val ARRIVAL_METERS = 40f
        private const val WATCH_LIMIT_MS = 2 * 60 * 60 * 1000L
        private const val UPDATE_MS = 5_000L
        /** Cada cuánto se envía la ubicación durante la ruta (si el cuidador lo activa). */
        private const val UPDATES_MS = 10 * 60 * 1000L
        /** Si pulsa el botón varias veces seguidas, un solo SMS cada 10 minutos. */
        private const val ALERT_GAP_MS = 10 * 60 * 1000L
        private var lastAlertAt = 0L

        fun alert(context: Context) {
            val now = SystemClock.elapsedRealtime()
            if (lastAlertAt != 0L && now - lastAlertAt < ALERT_GAP_MS) return
            lastAlertAt = now
            start(context, Intent(context, GoHomeService::class.java).setAction(ACTION_ALERT))
        }

        /** [debugIntervalSeconds] acorta el intervalo de los SMS para probar (solo en debug). */
        fun watch(context: Context, lat: Double, lng: Double, debugIntervalSeconds: Int = 0) = start(
            context,
            Intent(context, GoHomeService::class.java).setAction(ACTION_WATCH)
                .putExtra(EXTRA_LAT, lat).putExtra(EXTRA_LNG, lng)
                .putExtra(EXTRA_DEBUG_INTERVAL_SECONDS, debugIntervalSeconds)
        )

        private fun start(context: Context, intent: Intent) {
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo iniciar el servicio", e)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sending = 0
    private var listener: LocationListener? = null
    private var watchTimeout: Job? = null
    private var updates: Job? = null
    private var house: Location? = null
    private var lastLocation: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startLocationForeground(NOTIFICATION_ID, getString(R.string.notification_go_home))) {
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_ALERT -> sendAlert()
            ACTION_WATCH -> {
                val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                val intervalMs = intent.getIntExtra(EXTRA_DEBUG_INTERVAL_SECONDS, 0)
                    .takeIf { debuggable && it > 0 }?.let { it * 1000L } ?: UPDATES_MS
                startWatching(intent.getDoubleExtra(EXTRA_LAT, 0.0), intent.getDoubleExtra(EXTRA_LNG, 0.0), intervalMs)
            }
        }
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun sendAlert() = sendToSosContact("Aviso de vuelta a casa") {
        val location = currentLocation(this@GoHomeService)
        if (location != null) getString(R.string.go_home_sms, locationDetail(this@GoHomeService, location))
        else getString(R.string.go_home_sms_no_location)
    }

    /** Ubicación durante la ruta, con la distancia que le queda hasta casa. */
    private fun sendUpdate() = sendToSosContact("Ubicación de la ruta") {
        val location = lastLocation?.takeIf { System.currentTimeMillis() - it.time < 2 * 60_000 }
            ?: currentLocation(this@GoHomeService)
        val target = house
        if (location != null && target != null) {
            getString(
                R.string.go_home_update,
                locationDetail(this@GoHomeService, location),
                location.distanceTo(target).toInt()
            )
        } else {
            getString(R.string.go_home_update_no_location)
        }
    }

    /** SMS al contacto del SOS (con la batería al final). El servicio sigue vivo mientras envía. */
    private fun sendToSosContact(what: String, text: suspend () -> String) {
        sending++
        scope.launch {
            val contact = resolveSosContact(this@GoHomeService)
            if (contact != null) {
                val sent = sendSms(contact.phone, withBattery(this@GoHomeService, text()))
                Log.i(TAG, "$what a ${contact.name} enviado=$sent")
            }
            sending--
            stopIfIdle()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startWatching(lat: Double, lng: Double, updatesMs: Long) {
        stopWatching()
        val lm = getSystemService(LocationManager::class.java) ?: return
        val target = Location("house").apply { latitude = lat; longitude = lng }
        house = target
        val newListener = LocationListener { location ->
            lastLocation = location
            val meters = location.distanceTo(target)
            Log.d(TAG, "A $meters m de casa (±${location.accuracy} m)")
            if (meters <= ARRIVAL_METERS) arrived()
        }
        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, UPDATE_MS, 5f, newListener, Looper.getMainLooper())
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Sin permiso de ubicación", e)
            return
        }
        listener = newListener
        if (CaregiverSettings(this).goHomeUpdates) {
            updates = scope.launch {
                while (true) {
                    delay(updatesMs)
                    sendUpdate()
                }
            }
        }
        watchTimeout = scope.launch {
            delay(WATCH_LIMIT_MS)
            Log.i(TAG, "Ruta a casa: se deja de mirar el GPS (tiempo máximo)")
            stopWatching()
            stopIfIdle()
        }
    }

    /** Ha llegado: la pantalla del portal, encima de Google Maps. */
    private fun arrived() {
        Log.i(TAG, "Ha llegado a casa")
        // Con las actualizaciones activadas, un último SMS: ya no llegarán más.
        val notify = updates != null
        stopWatching()
        if (notify) sendToSosContact("Llegada a casa") { getString(R.string.go_home_arrived) }
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_ARRIVED_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo abrir la pantalla de llegada", e)
        }
        stopIfIdle()
    }

    private fun stopWatching() {
        watchTimeout?.cancel()
        watchTimeout = null
        updates?.cancel()
        updates = null
        listener?.let { getSystemService(LocationManager::class.java)?.removeUpdates(it) }
        listener = null
    }

    private fun stopIfIdle() {
        if (sending > 0 || listener != null) return
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopWatching()
        scope.cancel()
        super.onDestroy()
    }
}
