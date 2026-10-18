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

package com.launcherfacil.app.inbox

import android.content.Context
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
import com.launcherfacil.app.R
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
fun formatWhen(context: Context, millis: Long, is24h: Boolean): String {
    val time = DateFormat.format(if (is24h) "H:mm" else "h:mm a", Date(millis)).toString()
    return when {
        DateUtils.isToday(millis) -> time
        DateUtils.isToday(millis + DateUtils.DAY_IN_MILLIS) -> context.getString(R.string.yesterday_at, time)
        else -> {
            val day = DateFormat.format("d MMM", Date(millis)).toString().lowercase(Locale.getDefault())
            "$day $time"
        }
    }
}
