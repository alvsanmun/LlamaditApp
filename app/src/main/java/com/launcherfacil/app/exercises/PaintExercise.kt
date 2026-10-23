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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.TextoClaro
import kotlinx.coroutines.launch
import kotlin.math.min

private const val MAX_ZOOM = 4f
private val INK = Color(0xFF212121)

/**
 * Pintar por números, como en los cuadernos de colorear: un dibujo de líneas con un
 * número en cada zona. Se elige un color abajo y se toca una zona con ese número; se
 * pinta entera. El dibujo se puede ampliar con dos dedos o con los botones + y −. En
 * los niveles 1 a 3, las zonas del color elegido se marcan suavemente.
 * Bien hecho si no hay más de 3 fallos.
 */
@Composable
internal fun PaintExercise(level: Int, onDone: (Boolean) -> Unit) {
    val picture = remember { PAINT_PICTURES.filter { level in it.levels }.random() }
    val regions = picture.regions
    var painted by remember { mutableStateOf(List(regions.size) { false }) }
    var selected by remember { mutableIntStateOf(1) }
    var mistakes by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hints = level <= 3

    fun clamp(o: Offset): Offset {
        val maxX = (scale - 1) * boxSize.width / 2
        val maxY = (scale - 1) * boxSize.height / 2
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    /** Toque en coordenadas del dibujo (0–100): la zona de más arriba que lo contiene. */
    fun tap(x: Float, y: Float) {
        val i = regions.indices.lastOrNull { regions[it].contains(x, y) } ?: return
        if (painted[i]) return
        if (regions[i].color != selected) {
            mistakes++
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            scope.launch { shake.shake() }
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        painted = painted.toMutableList().also { it[i] = true }
        if (painted.all { it }) onDone(mistakes <= 3)
    }

    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .clipToBounds()
                .pointerInput(Unit) {
                    boxSize = size
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                        offset = clamp(offset + pan)
                    }
                }
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .pointerInput(picture) {
                        detectTapGestures { p ->
                            val s = min(size.width, size.height) / PAINT_SIZE
                            val left = (size.width - PAINT_SIZE * s) / 2
                            val top = (size.height - PAINT_SIZE * s) / 2
                            tap((p.x - left) / s, (p.y - top) / s)
                        }
                    }
            ) {
                val s = min(size.width, size.height) / PAINT_SIZE
                val left = (size.width - PAINT_SIZE * s) / 2
                val top = (size.height - PAINT_SIZE * s) / 2
                withTransform({
                    translate(left, top)
                    scale(s, s, pivot = Offset.Zero)
                }) {
                    val outline = Stroke(width = 0.8f, join = StrokeJoin.Round, cap = StrokeCap.Round)
                    regions.forEachIndexed { i, region ->
                        val fill = when {
                            painted[i] -> picture.colors[region.color - 1]
                            hints && region.color == selected ->
                                picture.colors[region.color - 1].copy(alpha = 0.25f).compositeOverWhite()
                            else -> Color.White
                        }
                        drawPath(region.composePath, fill)
                        drawPath(region.composePath, INK, style = outline)
                    }
                    picture.composeLines.forEach { drawPath(it, INK, style = outline) }
                }
                // Números de las zonas sin pintar.
                regions.forEachIndexed { i, region ->
                    if (painted[i]) return@forEachIndexed
                    val text = measurer.measure(
                        region.color.toString(),
                        TextStyle(fontSize = (region.labelSize * s).toSp(), fontWeight = FontWeight.Bold, color = INK)
                    )
                    drawText(
                        text,
                        topLeft = Offset(
                            left + region.label.x * s - text.size.width / 2f,
                            top + region.label.y * s - text.size.height / 2f
                        )
                    )
                }
            }
            Column(
                Modifier.align(Alignment.TopEnd).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZoomButton("+") { scale = (scale * 1.5f).coerceAtMost(MAX_ZOOM); offset = clamp(offset) }
                ZoomButton("−") { scale = (scale / 1.5f).coerceAtLeast(1f); offset = clamp(offset) }
            }
        }
        // Paleta: un círculo grande por color, con su número.
        Row(
            Modifier.fillMaxWidth().height(84.dp).shake(shake),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            picture.colors.forEachIndexed { index, color ->
                val number = index + 1
                val isSelected = number == selected
                Box(
                    Modifier
                        .size(if (isSelected) 76.dp else 60.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(if (isSelected) 6.dp else 2.dp, if (isSelected) TextoClaro else GrisBoton, CircleShape)
                        .clickable { selected = number },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        number.toString(),
                        color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun Color.compositeOverWhite() = Color(
    red = red * alpha + (1 - alpha),
    green = green * alpha + (1 - alpha),
    blue = blue * alpha + (1 - alpha)
)

@Composable
private fun ZoomButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(56.dp).clip(CircleShape).background(Color(0xCC455A64)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = TextoClaro, fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}
