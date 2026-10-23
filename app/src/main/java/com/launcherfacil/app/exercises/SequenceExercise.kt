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

package com.launcherfacil.app.exercises

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.ui.BateriaMedia
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.TextoClaro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botón de la secuencia: color y forma (por si no se distinguen los colores). */
private class Pad(val color: Color, val shape: String)

private val PADS = listOf(
    Pad(Color(0xFFE53935), "●"),
    Pad(Color(0xFF43A047), "■"),
    Pad(Color(0xFF1E88E5), "▲"),
    Pad(Color(0xFFFDD835), "★"),
    Pad(Color(0xFF8E24AA), "◆"),
    Pad(Color(0xFFFB8C00), "♥"),
)

/** Botones y largo de la secuencia por nivel. */
private val SEQUENCE_PADS = listOf(4, 4, 4, 6, 6)
private val SEQUENCE_LENGTH = listOf(2, 3, 4, 4, 5)

private const val MAX_MISTAKES = 3

/**
 * Repetir la secuencia: se encienden unos botones de colores y luego hay
 * que tocarlos en el mismo orden. Arriba, unos ojos mientras hay que mirar y una mano
 * cuando toca repetir; los ojos también vuelven a enseñar la secuencia.
 * Bien hecha si se repite a la primera.
 */
@Composable
internal fun SequenceExercise(level: Int, onDone: (Boolean) -> Unit) {
    val pads = PADS.take(SEQUENCE_PADS[level - 1])
    val sequence = remember { List(SEQUENCE_LENGTH[level - 1]) { pads.indices.random() } }
    var showing by remember { mutableIntStateOf(0) } // cada cambio vuelve a enseñarla
    var watching by remember { mutableStateOf(true) }
    var lit by remember { mutableStateOf<Int?>(null) }
    var position by remember { mutableIntStateOf(0) }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(showing) {
        watching = true
        position = 0
        delay(900)
        for (pad in sequence) {
            lit = pad
            delay(650)
            lit = null
            delay(300)
        }
        watching = false
    }

    fun tap(pad: Int) {
        if (watching || finished) return
        scope.launch {
            lit = pad
            delay(250)
            if (lit == pad) lit = null
        }
        if (pad == sequence[position]) {
            position++
            if (position == sequence.size) {
                finished = true
                scope.launch {
                    delay(400)
                    onDone(mistakes == 0)
                }
            }
            return
        }
        mistakes++
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shake.shake()
            if (mistakes >= MAX_MISTAKES) {
                finished = true
                onDone(false)
            } else {
                delay(400)
                showing++
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ojos (mirar) o mano (repetir); al tocar, se vuelve a ver la secuencia.
            BigTile(GrisBoton, { if (!watching && !finished) showing++ }, Modifier.width(120.dp).fillMaxHeight()) {
                FitText(if (watching) "👀" else "👆", Modifier.fillMaxSize())
            }
            // Cuántos pasos lleva: un punto por cada botón de la secuencia.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                sequence.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (i < position) BateriaMedia else Color.Transparent)
                            .border(3.dp, BateriaMedia, CircleShape)
                    )
                }
            }
        }
        val cols = 2
        Column(Modifier.weight(1f).fillMaxWidth().shake(shake), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            pads.indices.chunked(cols).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { i ->
                        val on = lit == i
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(28.dp))
                                .background(if (on) pads[i].color else pads[i].color.copy(alpha = 0.35f))
                                .then(if (on) Modifier.border(8.dp, TextoClaro, RoundedCornerShape(28.dp)) else Modifier)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { tap(i) }
                        ) {
                            FitText(pads[i].shape, Modifier.fillMaxSize(), fraction = 0.45f,
                                color = if (on) TextoClaro else TextoClaro.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}
