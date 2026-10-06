package com.launcherfacil.app.inbox

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Date
import java.util.Locale

/**
 * Número que cambia cada vez que cambia el contenido de [uris] (llega un SMS, una
 * llamada...). Sirve de clave para volver a cargar listas y contadores.
 */
@Composable
fun rememberContentVersion(vararg uris: Uri): Int {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    DisposableEffect(*uris) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                version++
            }
        }
        for (uri in uris) {
            try {
                context.contentResolver.registerContentObserver(uri, true, observer)
            } catch (e: SecurityException) {
                // Sin permiso todavía: no se actualiza solo, pero no falla.
            }
        }
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return version
}

/** "14:32" si es de hoy, "Ayer 09:10", o "3 oct 18:20". */
fun formatWhen(millis: Long, is24h: Boolean): String {
    val time = DateFormat.format(if (is24h) "H:mm" else "h:mm a", Date(millis)).toString()
    return when {
        DateUtils.isToday(millis) -> time
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> "Ayer $time"
        else -> {
            val day = DateFormat.format("d MMM", Date(millis)).toString().lowercase(Locale.getDefault())
            "$day $time"
        }
    }
}
