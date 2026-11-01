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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Botón de la secuencia: color, color encendido, relieve y forma (por si no se distinguen los colores). */
private class Pad(val color: Color, val lit: Color, val dark: Color, @DrawableRes val shape: Int, val ink: Color = Color.White)

private val PADS = listOf(
    Pad(Color(0xFFE53935), Color(0xFFEF5350), Color(0xFF9A1B19), R.drawable.ic_shape_circle),
    Pad(Color(0xFF2E7D32), Color(0xFF43A047), Color(0xFF1B4D1F), R.drawable.ic_shape_square),
    Pad(Color(0xFF1E88E5), Color(0xFF42A5F5), Color(0xFF0D47A1), R.drawable.ic_shape_triangle),
    Pad(Color(0xFFFBC02D), Color(0xFFFFD54F), Color(0xFFA87F00), R.drawable.ic_shape_star, Color(0xFF3E2A00)),
    Pad(Color(0xFF8E44AD), Color(0xFFAB47BC), Color(0xFF5B2479), R.drawable.ic_shape_diamond),
    Pad(Color(0xFFEF6C00), Color(0xFFFF9800), Color(0xFFA84300), R.drawable.ic_shape_heart),
)

/** Botones y largo de la secuencia por nivel. */
private val SEQUENCE_PADS = listOf(4, 4, 4, 6, 6)
private val SEQUENCE_LENGTH = listOf(2, 3, 4, 4, 5)

private const val MAX_MISTAKES = 3

/**
 * Repetir la secuencia (diseño "Secuencia mira ahora tú"): arriba, al lado de la casa,
 * dos indicadores: el ojo (mira) y la mano (ahora tú), encendido el que toca. Debajo,
 * la secuencia que se va completando y los botones grandes de colores con su forma.
 * Tocar el ojo vuelve a enseñarla. Bien hecha si se repite a la primera.
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
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Mira / Ahora tú. Tocar el ojo vuelve a enseñar la secuencia.
        Row(
            Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(22.dp)).background(GmCard).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PhaseChip(R.drawable.ic_game_eye, watching, GmBlue, Modifier.weight(1f)) {
                if (!watching && !finished) showing++
            }
            PhaseChip(R.drawable.ic_game_hand, !watching && !finished, GmGreenDark, Modifier.weight(1f), null)
        }
        // La secuencia: hechos con su color y forma; los que faltan, huecos discontinuos.
        Row(
            Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(24.dp)).background(GmCard).padding(horizontal = 12.dp).shake(shake),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            sequence.forEachIndexed { i, pad ->
                val slot = Modifier.weight(1f, fill = false).widthIn(max = 62.dp).aspectRatio(1f)
                if (i < position) {
                    Box(slot.clip(RoundedCornerShape(16.dp)).background(pads[pad].color), contentAlignment = Alignment.Center) {
                        Icon(painterResource(pads[pad].shape), null, tint = pads[pad].ink, modifier = Modifier.fillMaxSize(0.5f))
                    }
                } else {
                    Box(slot.drawBehind {
                        drawRoundRect(
                            GmDashed,
                            cornerRadius = CornerRadius(16.dp.toPx()),
                            style = Stroke(4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())))
                        )
                    })
                }
            }
        }
        // Los botones grandes, de dos en dos.
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            pads.indices.chunked(2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { i -> SequencePad(pads[i], lit == i, Modifier.weight(1f).fillMaxHeight()) { tap(i) } }
                }
            }
        }
    }
}

/** Indicador de la fase: encendido con su color y el dibujo en blanco; apagado, claro. */
@Composable
private fun PhaseChip(@DrawableRes icon: Int, active: Boolean, color: Color, modifier: Modifier, onClick: (() -> Unit)?) {
    Box(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) color else GmSand)
            .alpha(if (active) 1f else 0.45f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(painterResource(icon), null, tint = if (active) Color.White else GmInk, modifier = Modifier.size(40.dp))
    }
}

/**
 * Botón de la secuencia con relieve. Encendido (al enseñarla o al tocarlo): más
 * claro, con borde blanco y un anillo de su color oscuro, y hundido.
 */
@Composable
private fun SequencePad(pad: Pad, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(30.dp)
    Box(modifier) {
        Box(Modifier.matchParentSize().padding(top = 8.dp).clip(shape).background(pad.dark))
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = 8.dp)
                .offset(y = if (on) 5.dp else 0.dp)
                .then(if (on) Modifier.border(6.dp, pad.dark, shape).padding(6.dp).border(6.dp, Color.White, RoundedCornerShape(24.dp)) else Modifier)
                .clip(shape)
                .background(if (on) pad.lit else pad.color)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(pad.shape), null, tint = pad.ink, modifier = Modifier.fillMaxSize(0.45f))
        }
    }
}
