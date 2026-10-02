package com.launcherfacil.app.sos

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.launcherfacil.app.contacts.loadFavoriteContacts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Tras pulsar SOS: envía un SMS con la ubicación al contacto de confianza y
 * después [UPDATES] actualizaciones, una cada [INTERVAL_MS]. Luego se detiene solo.
 * Pulsar SOS otra vez reinicia la secuencia.
 */
class SosService : Service() {

    companion object {
        const val UPDATES = 3
        const val INTERVAL_MS = 10 * 60 * 1000L

        /** Solo en builds de depuración: acorta el intervalo para probar. */
        const val EXTRA_DEBUG_INTERVAL_SECONDS = "debug_interval_seconds"

        private const val TAG = "SosService"
        private const val NOTIFICATION_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Sin ubicación (o sin poder pasar a primer plano) las actualizaciones no
        // aportan nada: un solo SMS.
        val foreground = hasLocation &&
            startLocationForeground(NOTIFICATION_ID, "SOS activo: enviando la ubicación durante 30 minutos")
        val updates = if (foreground) UPDATES else 0

        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val intervalMs = intent?.getIntExtra(EXTRA_DEBUG_INTERVAL_SECONDS, 0)
            ?.takeIf { debuggable && it > 0 }
            ?.let { it * 1000L }
            ?: INTERVAL_MS

        job?.cancel()
        job = scope.launch {
            val contact = loadFavoriteContacts(this@SosService).firstOrNull()
            if (contact == null) {
                Log.w(TAG, "No hay contacto de confianza: no se envía SMS")
            } else {
                for (i in 0..updates) {
                    if (i > 0) delay(intervalMs)
                    val location = currentLocation(this@SosService)
                    val text = message(location, i, updates)
                    val sent = sendSms(contact.phone, text)
                    Log.i(TAG, "SMS ${i + 1}/${updates + 1} enviado=$sent: $text")
                }
            }
            ServiceCompat.stopForeground(this@SosService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun message(location: Location?, index: Int, total: Int): String {
        val where = if (location != null) {
            val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(location.time))
            "${location.mapsLink()} (±${location.accuracy.toInt()} m, $time)"
        } else {
            "no se ha podido obtener la ubicación."
        }
        return when {
            index == 0 -> "SOS: necesito ayuda. Estoy aquí: $where"
            index < total -> "SOS: ubicación actualizada ($index/$total): $where"
            else -> "SOS: última ubicación ($index/$total): $where"
        }
    }
}
