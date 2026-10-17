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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings.Companion.PIN_LENGTH

/**
 * PIN del cuidador.
 * - [creating] = false: pide el PIN y llama a [onCheck]; si es correcto, a [onSuccess].
 * - [creating] = true: pide un PIN nuevo dos veces y lo entrega a [onCreated].
 */
@Composable
fun PinScreen(
    creating: Boolean,
    onCheck: (String) -> Boolean,
    onSuccess: () -> Unit,
    onCreated: (String) -> Unit,
    onHome: () -> Unit
) {
    var pin by rememberSaveable { mutableStateOf("") }
    var firstPin by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val title = when {
        !creating -> "PIN del cuidador"
        firstPin == null -> "Crea un PIN de $PIN_LENGTH cifras"
        else -> "Repite el PIN"
    }

    fun complete(value: String) {
        when {
            !creating -> if (onCheck(value)) onSuccess() else error = "PIN incorrecto"
            firstPin == null -> {
                firstPin = value
                error = null
            }
            value == firstPin -> onCreated(value)
            else -> {
                firstPin = null
                error = "Los PIN no coinciden. Vuelve a empezar."
            }
        }
        pin = ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeBar(onHome)
        Text(
            title,
            color = TextoClaro,
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        // Un punto por cifra: relleno = ya escrita.
        Row(
            modifier = Modifier.fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(PIN_LENGTH) { i ->
                Box(
                    Modifier
                        .size(24.dp)
                        .background(if (i < pin.length) TextoClaro else GrisBoton, CircleShape)
                )
            }
        }
        Text(
            error.orEmpty(),
            color = BateriaBaja,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().height(28.dp)
        )

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        for (row in rows) {
            KeyRow {
                for (digit in row) {
                    DigitKey(digit, Modifier.weight(1f)) {
                        pin += digit
                        if (pin.length == PIN_LENGTH) complete(pin)
                    }
                }
            }
        }
        KeyRow {
            KeyBox(color = GrisBoton, onClick = { pin = pin.dropLast(1) }, modifier = Modifier.weight(1f)) {
                TileIcon(R.drawable.ic_backspace, 0.5f)
            }
            DigitKey("0", Modifier.weight(1f)) {
                pin += "0"
                if (pin.length == PIN_LENGTH) complete(pin)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
