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

package com.launcherfacil.app.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Hora actual truncada al minuto; se actualiza justo al cambiar de minuto. */
@Composable
private fun rememberCurrentMinute(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)) }
    LaunchedEffect(Unit) {
        while (true) {
            val millis = ChronoUnit.MILLIS.between(LocalDateTime.now(), now.plusMinutes(1))
            delay(millis.coerceAtLeast(0) + 50)
            now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
        }
    }
    return now
}

@Composable
fun BigClock(modifier: Modifier = Modifier) {
    val now = rememberCurrentMinute()
    val is24h = DateFormat.is24HourFormat(LocalContext.current)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24h) "H:mm" else "h:mm"))
    val locale = Locale.getDefault()
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = time,
            color = TextoClaro,
            fontSize = 120.sp,
            lineHeight = 120.sp,
            fontWeight = FontWeight.Bold
        )
        Text(text = date, color = TextoSuave, fontSize = 28.sp)
    }
}
