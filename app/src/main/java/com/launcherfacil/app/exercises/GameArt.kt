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

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import kotlin.math.min

/** Lienzo de los dibujos de las tarjetas (diseño "Dibujos 3 · La mano hace la acción"). */
private const val ART_W = 120f
private const val ART_H = 84f

private val WHITE = Color.White
private val YELLOW = Color(0xFFFFD54F)
private val SKIN = Color(0xFFFFCC80)
private val SKIN_LINE = Color(0xFF4E342E)

/** Mano con el dedo índice tocando (la misma en todos los dibujos). */
private const val HAND =
    "M9 11V4.5a1.5 1.5 0 0 1 3 0V10h1a1.5 1.5 0 0 1 1.5 1.2 1.5 1.5 0 0 1 2.5 0.3 1.5 1.5 0 0 1 2.5 1V17a5 5 0 0 1-5 5h-1.5a5 5 0 0 1-4-2l-3.6-4.8a1.4 1.4 0 0 1 2.1-1.8L9 15z"

private val pathCache = HashMap<String, Path>()

private fun path(d: String): Path = pathCache.getOrPut(d) { PathParser.createPathFromPathData(d).asComposePath() }

/**
 * Dibujo de la tarjeta de cada juego: una escena pequeña en la que una mano hace lo
 * que se hace en el juego (tocar la respuesta, levantar una carta, pintar un pétalo…),
 * para reconocerlo sin leer. Se escala para caber centrado en su caja.
 */
@Composable
internal fun GameArt(exercise: Exercise, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = min(size.width / ART_W, size.height / ART_H)
        withTransform({
            translate((size.width - ART_W * s) / 2, (size.height - ART_H * s) / 2)
            scale(s, s, pivot = Offset.Zero)
        }) {
            val art = Art(this)
            when (exercise) {
                Exercise.MATH -> art.math()
                Exercise.PAIRS -> art.pairs()
                Exercise.PAINT -> art.paint()
                Exercise.WORDS -> art.words()
                Exercise.FACES -> art.faces()
                Exercise.SEQUENCE -> art.sequence()
                Exercise.MONEY -> art.money()
                Exercise.PUZZLE -> art.puzzle()
            }
        }
    }
}

/** Pequeño ayudante para dibujar en las coordenadas del diseño (120 × 84). */
private class Art(val d: DrawScope) {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    fun rect(x: Float, y: Float, w: Float, h: Float, r: Float, fill: Color, stroke: Color? = null, sw: Float = 0f) {
        d.drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(r))
        if (stroke != null) d.drawRoundRect(stroke, Offset(x, y), Size(w, h), CornerRadius(r), style = Stroke(sw))
    }

    fun circle(cx: Float, cy: Float, r: Float, fill: Color, stroke: Color? = null, sw: Float = 0f) {
        d.drawCircle(fill, r, Offset(cx, cy))
        if (stroke != null) d.drawCircle(stroke, r, Offset(cx, cy), style = Stroke(sw))
    }

    fun fill(p: String, color: Color) = d.drawPath(path(p), color)

    fun line(p: String, color: Color, sw: Float) =
        d.drawPath(path(p), color, style = Stroke(sw, cap = StrokeCap.Round, join = StrokeJoin.Round))

    /** Texto centrado en x con la línea base en y, en negrita. */
    fun text(x: Float, y: Float, size: Float, color: Color, value: String) {
        // Con el lienzo nativo (Compose no dibuja texto sin medirlo antes); ya va escalado.
        textPaint.textSize = size
        textPaint.color = color.toArgb()
        d.drawContext.canvas.nativeCanvas.drawText(value, x, y, textPaint)
    }

    fun hand(tx: Float, ty: Float, s: Float) {
        d.translate(tx, ty) {
            scale(s, s, pivot = Offset.Zero) {
                drawPath(path(HAND), SKIN)
                drawPath(path(HAND), SKIN_LINE, style = Stroke(1.1f, join = StrokeJoin.Round))
            }
        }
    }

    // Cálculo: "2 + 1" y tres respuestas; la mano toca la buena (amarilla).
    fun math() {
        text(60f, 30f, 24f, WHITE, "2 + 1")
        rect(10f, 42f, 28f, 28f, 7f, WHITE); text(24f, 63f, 20f, Color(0xFF1565C0), "4")
        rect(46f, 42f, 28f, 28f, 7f, YELLOW, WHITE, 3f); text(60f, 63f, 20f, Color(0xFF3E2A00), "3")
        rect(82f, 42f, 28f, 28f, 7f, WHITE); text(96f, 63f, 20f, Color(0xFF1565C0), "5")
        hand(43f, 52f, 2.1f)
    }

    // Parejas: dos cartas con estrella y la de en medio que se levanta.
    fun pairs() {
        val star1 = "M23 22l3.3 6.6 7.2 1-5.2 5 1.2 7.2L23 38.4l-6.5 3.4 1.2-7.2-5.2-5 7.2-1z"
        val star2 = "M97 22l3.3 6.6 7.2 1-5.2 5 1.2 7.2L97 38.4l-6.5 3.4 1.2-7.2-5.2-5 7.2-1z"
        rect(8f, 10f, 30f, 44f, 6f, WHITE); fill(star1, Color(0xFFF9A825))
        rect(45f, 10f, 30f, 44f, 6f, Color(0xFFB388D1), WHITE, 2.5f)
        rect(82f, 10f, 30f, 44f, 6f, WHITE); fill(star2, Color(0xFFF9A825))
        hand(37f, 34f, 2.1f)
    }

    // Pintar: una flor con un pétalo ya pintado y dos colores para elegir.
    fun paint() {
        line("M44 54v28", WHITE, 4f)
        circle(44f, 20f, 11f, WHITE)
        circle(59f, 33f, 11f, WHITE)
        circle(53f, 50f, 11f, Color(0xFFEC407A), WHITE, 2.5f)
        circle(35f, 50f, 11f, WHITE)
        circle(29f, 33f, 11f, WHITE)
        circle(44f, 36f, 7f, YELLOW)
        circle(88f, 18f, 7f, Color(0xFFEC407A), WHITE, 2.5f)
        circle(106f, 18f, 7f, Color(0xFF42A5F5), WHITE, 2.5f)
        hand(36f, 46f, 2.1f)
    }

    // Sopa de letras: la palabra SOL marcada y un sol al lado.
    fun words() {
        val ink = Color(0xFF00574D)
        val dark = Color(0xFF3E2A00)
        rect(6f, 8f, 76f, 70f, 8f, WHITE)
        rect(10f, 30f, 56f, 20f, 10f, YELLOW)
        listOf("M", "A", "R", "P").forEachIndexed { i, c -> text(listOf(20f, 38f, 56f, 72f)[i], 25f, 15f, ink, c) }
        listOf("S", "O", "L").forEachIndexed { i, c -> text(listOf(20f, 38f, 56f)[i], 46f, 15f, dark, c) }
        text(72f, 46f, 15f, ink, "E")
        listOf("T", "U", "C", "A").forEachIndexed { i, c -> text(listOf(20f, 38f, 56f, 72f)[i], 68f, 15f, ink, c) }
        circle(102f, 26f, 9f, YELLOW)
        line("M102 10v4M102 38v4M86 26h4M114 26h4", YELLOW, 3f)
        hand(43f, 38f, 2.1f)
    }

    // ¿Quién es?: un altavoz que habla y tres caras; la mano toca la del medio.
    fun faces() {
        fill("M48 8h6l8-6v18l-8-6h-6z", WHITE)
        line("M66 6q4 5 0 10M70 2q8 9 0 18", WHITE, 2.5f)
        face(22f, Color(0xFF9CC3E6), Color(0xFF3E6E9C), WHITE, 3f)
        face(60f, Color(0xFFF4B183), Color(0xFFB5643A), YELLOW, 5f)
        face(98f, Color(0xFFE2C7F0), Color(0xFF8C5AA8), WHITE, 3f)
        hand(46f, 44f, 1.9f)
    }

    private fun face(cx: Float, bg: Color, fg: Color, ring: Color, ringWidth: Float) {
        circle(cx, 46f, 16f, bg, ring, ringWidth)
        circle(cx, 42f, 5.5f, fg)
        fill("M${cx - 10} 57q10-11 20 0", fg)
    }

    // Secuencia: los cuatro botones de colores con su forma; la mano toca el azul.
    fun sequence() {
        rect(16f, 6f, 88f, 74f, 10f, WHITE)
        rect(22f, 12f, 36f, 29f, 6f, Color(0xFFE53935)); circle(40f, 26.5f, 7f, WHITE)
        rect(62f, 12f, 36f, 29f, 6f, Color(0xFF2E7D32)); rect(74f, 20.5f, 12f, 12f, 0f, WHITE)
        rect(22f, 45f, 36f, 29f, 6f, Color(0xFF64B5F6), Color(0xFF1565C0), 3f); fill("M40 52l7 13H33z", WHITE)
        rect(62f, 45f, 36f, 29f, 6f, Color(0xFFFBC02D))
        fill("M80 51l2.2 4.6 5 0.7-3.6 3.4 0.9 5-4.5-2.4-4.5 2.4 0.9-5-3.6-3.4 5-0.7z", Color(0xFF3E2A00))
        hand(20f, 52f, 2f)
    }

    // Dinero: una etiqueta de precio y las monedas para pagarlo.
    fun money() {
        fill("M78 8h32v22H78l-8-11z", WHITE)
        circle(78f, 19f, 2.5f, Color(0xFF8D6E00))
        text(96f, 25f, 15f, Color(0xFF5C4800), "2€")
        circle(26f, 46f, 15f, YELLOW, WHITE, 3f); text(26f, 51f, 13f, Color(0xFF5D4500), "1€")
        circle(62f, 54f, 15f, YELLOW, WHITE, 3f); text(62f, 59f, 13f, Color(0xFF5D4500), "1€")
        circle(98f, 62f, 11f, Color(0xFFE0B07A), WHITE, 2.5f); text(98f, 66f, 9f, Color(0xFF4E2E0E), "50")
        hand(40f, 50f, 2f)
    }

    // Puzle: un paisaje con un hueco y la pieza que lleva la mano.
    fun puzzle() {
        rect(6f, 8f, 70f, 60f, 4f, Color(0xFF90CAF9))
        circle(22f, 22f, 7f, YELLOW)
        fill("M6 54q18-20 36-6t34-4v24H6z", Color(0xFF66BB6A))
        d.drawRect(Color(0x733E2723), Offset(42f, 38f), Size(34f, 30f))
        d.drawRect(WHITE, Offset(42f, 38f), Size(34f, 30f), style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))))
        line("M86 30q-6-10-20 2", WHITE, 3f)
        line("M64 26l2 7 7-2", WHITE, 3f)
        d.rotate(10f, Offset(100f, 50f)) {
            drawRect(Color(0xFF90CAF9), Offset(84f, 36f), Size(30f, 28f))
            drawPath(path("M84 52q10-6 30-4v16H84z"), Color(0xFF66BB6A))
            drawRect(WHITE, Offset(84f, 36f), Size(30f, 28f), style = Stroke(2.5f))
        }
        hand(78f, 48f, 1.9f)
    }
}
