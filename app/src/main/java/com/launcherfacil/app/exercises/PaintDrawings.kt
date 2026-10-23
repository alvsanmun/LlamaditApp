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

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath

/** Lado del lienzo de los dibujos (coordenadas de 0 a 100). */
internal const val PAINT_SIZE = 100f

/**
 * Una zona del dibujo: su contorno, el número de su color y dónde va el número
 * (por defecto, en el centro; hay que moverlo si otra zona la tapa por el medio).
 */
internal class PaintRegion(val path: Path, val color: Int, label: Offset?, val labelSize: Float) {
    val composePath = path.asComposePath()
    val label: Offset = label ?: RectF().also { path.computeBounds(it, true) }.let { Offset(it.centerX(), it.centerY()) }

    /** Para saber qué zona se ha tocado (a 10 × para no perder precisión). */
    private val region = Region().apply {
        val scaled = Path(path).apply { transform(Matrix().apply { setScale(10f, 10f) }) }
        setPath(scaled, Region(0, 0, 1000, 1000))
    }

    fun contains(x: Float, y: Float) = region.contains((x * 10).toInt(), (y * 10).toInt())
}

/**
 * Dibujo para pintar por números, de líneas negras como los de los cuadernos de
 * colorear. Las zonas se dibujan en orden (las de después, encima). [lines] son trazos
 * que no se pintan (bigotes, antenas…).
 */
internal class PaintPicture(val levels: IntRange, val colors: List<Color>, val regions: List<PaintRegion>, val lines: List<Path>) {
    val composeLines = lines.map { it.asComposePath() }
}

private class PictureBuilder {
    val regions = mutableListOf<PaintRegion>()
    val lines = mutableListOf<Path>()

    fun zone(color: Int, path: Path, label: Offset? = null, size: Float = 6f) {
        regions += PaintRegion(path, color, label, size)
    }

    fun line(vararg xy: Float) {
        lines += Path().apply {
            moveTo(xy[0], xy[1])
            for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
        }
    }
}

private fun picture(levels: IntRange, colors: List<Color>, build: PictureBuilder.() -> Unit): PaintPicture {
    val builder = PictureBuilder().apply(build)
    return PaintPicture(levels, colors, builder.regions, builder.lines)
}

private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }

private fun oval(cx: Float, cy: Float, rx: Float, ry: Float, degrees: Float = 0f) = Path().apply {
    addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW)
    if (degrees != 0f) transform(Matrix().apply { setRotate(degrees, cx, cy) })
}

private fun rect(left: Float, top: Float, right: Float, bottom: Float, radius: Float = 0f) = Path().apply {
    addRoundRect(RectF(left, top, right, bottom), radius, radius, Path.Direction.CW)
}

private fun poly(vararg xy: Float) = Path().apply {
    moveTo(xy[0], xy[1])
    for (i in 2 until xy.size step 2) lineTo(xy[i], xy[i + 1])
    close()
}

/** Mitad de arriba de un óvalo (el sombrero de una seta). */
private fun dome(left: Float, top: Float, right: Float, bottom: Float) = Path().apply {
    arcTo(RectF(left, top, right, bottom), 180f, 180f, true)
    close()
}

private val RED = Color(0xFFE53935)
private val PINK = Color(0xFFF48FB1)
private val YELLOW = Color(0xFFFDD835)
private val ORANGE = Color(0xFFFB8C00)
private val GREEN = Color(0xFF43A047)
private val BROWN = Color(0xFF8D6E63)
private val LIGHT_BLUE = Color(0xFF81D4FA)
private val BLUE = Color(0xFF1E88E5)
private val PURPLE = Color(0xFF8E24AA)
private val BLACK = Color(0xFF263238)
private val BEIGE = Color(0xFFFFCC80)
private val GREY = Color(0xFF90A4AE)

/** Dibujos de pocas zonas grandes (nivel 1) a muchas y pequeñas (nivel 5). */
internal val PAINT_PICTURES: List<PaintPicture> = listOf(
    // Manzana
    picture(1..1, listOf(RED, BROWN, GREEN)) {
        zone(1, oval(50f, 60f, 30f, 28f))
        zone(2, rect(47f, 20f, 53f, 36f, 1f), label = Offset(50f, 26f), size = 4f)
        zone(3, oval(64f, 26f, 11f, 5.5f, -30f))
    },
    // Helado
    picture(1..1, listOf(BEIGE, PINK, RED)) {
        zone(1, poly(30f, 45f, 70f, 45f, 50f, 93f), label = Offset(50f, 70f))
        zone(2, oval(50f, 42f, 23f, 18f))
        zone(3, circle(50f, 20f, 7f), size = 5f)
    },
    // Casa sencilla
    picture(1..1, listOf(YELLOW, RED, BROWN)) {
        zone(1, rect(22f, 45f, 78f, 88f), label = Offset(32f, 57f))
        zone(2, poly(14f, 47f, 50f, 14f, 86f, 47f), label = Offset(50f, 36f))
        zone(3, rect(42f, 60f, 58f, 88f), label = Offset(50f, 75f))
    },
    // Flor
    picture(2..2, listOf(PINK, YELLOW, GREEN)) {
        zone(3, rect(48f, 45f, 52f, 96f), label = Offset(50f, 90f), size = 4f)
        zone(3, oval(39f, 74f, 11f, 4.5f, -30f))
        zone(3, oval(61f, 82f, 11f, 4.5f, 30f))
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0)
            val x = 50f + 15f * kotlin.math.cos(a).toFloat()
            val y = 38f + 15f * kotlin.math.sin(a).toFloat()
            zone(1, circle(x, y, 11f), label = Offset(50f + 20f * kotlin.math.cos(a).toFloat(), 38f + 20f * kotlin.math.sin(a).toFloat()), size = 5f)
        }
        zone(2, circle(50f, 38f, 10f))
    },
    // Pez
    picture(2..2, listOf(ORANGE, YELLOW, BLACK, LIGHT_BLUE)) {
        zone(2, poly(70f, 50f, 93f, 31f, 93f, 69f), label = Offset(86f, 50f))
        zone(2, poly(36f, 36f, 62f, 36f, 47f, 17f), label = Offset(48f, 29f), size = 5f)
        zone(1, oval(48f, 52f, 28f, 18f), label = Offset(54f, 54f))
        zone(3, circle(32f, 47f, 4.5f), size = 4f)
        zone(4, circle(17f, 25f, 5f), size = 4f)
        zone(4, circle(10f, 12f, 4f), size = 3.5f)
        zone(4, circle(22f, 8f, 3.5f), size = 3f)
        line(26f, 60f, 32f, 58f)
    },
    // Seta
    picture(2..2, listOf(RED, YELLOW, BEIGE, GREEN)) {
        zone(4, rect(5f, 85f, 95f, 97f, 3f), label = Offset(20f, 91f), size = 5f)
        zone(3, rect(40f, 50f, 60f, 88f, 4f), label = Offset(50f, 72f))
        zone(1, dome(14f, 18f, 86f, 92f), label = Offset(48f, 48f))
        zone(2, circle(33f, 41f, 5.5f), size = 4.5f)
        zone(2, circle(54f, 30f, 6.5f), size = 5f)
        zone(2, circle(71f, 45f, 5f), size = 4.5f)
    },
    // Coche
    picture(3..5, listOf(RED, LIGHT_BLUE, BLACK, GREY, YELLOW)) {
        zone(4, rect(0f, 82f, 100f, 96f), label = Offset(10f, 89f), size = 5f)
        zone(1, poly(28f, 50f, 37f, 30f, 67f, 30f, 78f, 50f), label = Offset(52f, 34f), size = 4f)
        zone(2, poly(37f, 48f, 42f, 38f, 49f, 38f, 49f, 48f), size = 4f)
        zone(2, poly(55f, 48f, 55f, 38f, 63f, 38f, 69f, 48f), size = 4f)
        zone(1, rect(10f, 48f, 90f, 72f, 6f), label = Offset(50f, 60f))
        zone(5, circle(84f, 56f, 3.5f), size = 3.5f)
        zone(3, circle(30f, 72f, 10f), label = Offset(30f, 79f), size = 4f)
        zone(3, circle(70f, 72f, 10f), label = Offset(70f, 79f), size = 4f)
        zone(4, circle(30f, 72f, 4.5f), size = 4f)
        zone(4, circle(70f, 72f, 4.5f), size = 4f)
    },
    // Barco
    picture(3..4, listOf(BROWN, YELLOW, ORANGE, RED, BLUE)) {
        zone(5, rect(0f, 72f, 100f, 100f), label = Offset(12f, 90f))
        zone(2, circle(84f, 16f, 9f))
        zone(1, rect(48f, 14f, 52f, 70f), label = Offset(50f, 66.5f), size = 3f)
        zone(2, poly(46f, 20f, 46f, 62f, 18f, 62f), label = Offset(38f, 50f))
        zone(3, poly(54f, 28f, 54f, 62f, 78f, 62f), label = Offset(61f, 52f))
        zone(4, poly(52f, 14f, 65f, 18f, 52f, 22f), size = 4f)
        zone(1, poly(14f, 64f, 86f, 64f, 75f, 81f, 25f, 81f), label = Offset(50f, 72f))
    },
    // Pájaro en una rama (como un tucán)
    picture(3..4, listOf(BLACK, ORANGE, BROWN, GREEN, YELLOW)) {
        zone(1, poly(37f, 68f, 51f, 68f, 48f, 96f, 40f, 96f), label = Offset(44f, 88f), size = 5f)
        zone(4, oval(78f, 63f, 7f, 3.5f, -50f), size = 3.5f)
        zone(4, oval(89f, 64f, 7f, 3.5f, 40f), size = 3.5f)
        zone(4, oval(17f, 86f, 7f, 3.5f, 40f), size = 3.5f)
        zone(4, oval(28f, 87f, 7f, 3.5f, -40f), size = 3.5f)
        zone(3, rect(6f, 72f, 94f, 79f, 3.5f), label = Offset(78f, 75.5f), size = 5f)
        zone(1, oval(45f, 52f, 16f, 23f), label = Offset(33.5f, 52f), size = 5f)
        zone(5, oval(49f, 57f, 9f, 14f))
        zone(1, circle(47f, 28f, 12f), label = Offset(41f, 30f), size = 5f)
        zone(5, circle(51f, 24f, 3.5f), size = 3f)
        zone(2, poly(56f, 21f, 84f, 26f, 59f, 36f), label = Offset(67f, 27f), size = 5f)
        zone(2, oval(41f, 74f, 3.5f, 2.5f), size = 2.5f)
        zone(2, oval(52f, 74f, 3.5f, 2.5f), size = 2.5f)
    },
    // Mariposa
    picture(4..5, listOf(PURPLE, YELLOW, BLACK, PINK)) {
        zone(1, oval(31f, 36f, 18f, 13f, -30f), label = Offset(22f, 40f))
        zone(1, oval(69f, 36f, 18f, 13f, 30f), label = Offset(78f, 40f))
        zone(4, oval(35f, 65f, 13f, 10f, 30f), label = Offset(27f, 61f), size = 5f)
        zone(4, oval(65f, 65f, 13f, 10f, -30f), label = Offset(73f, 61f), size = 5f)
        zone(2, circle(31f, 32f, 5f), size = 4.5f)
        zone(2, circle(69f, 32f, 5f), size = 4.5f)
        zone(2, circle(37f, 68f, 4f), size = 4f)
        zone(2, circle(63f, 68f, 4f), size = 4f)
        zone(3, oval(50f, 52f, 4f, 22f), size = 4f)
        zone(3, circle(50f, 26f, 5f), size = 4f)
        line(48f, 22f, 40f, 8f)
        line(52f, 22f, 60f, 8f)
    },
    // Gato
    picture(4..5, listOf(ORANGE, PINK, GREEN, YELLOW)) {
        zone(1, oval(78f, 76f, 6f, 18f, -35f), label = Offset(80f, 73f), size = 5f)
        zone(1, oval(50f, 76f, 22f, 20f), label = Offset(34f, 76f), size = 5f)
        zone(4, oval(50f, 81f, 11f, 13f))
        zone(1, poly(27f, 32f, 30f, 6f, 47f, 20f), label = Offset(31f, 17f), size = 4f)
        zone(1, poly(73f, 32f, 70f, 6f, 53f, 20f), label = Offset(69f, 17f), size = 4f)
        zone(2, poly(33f, 22f, 33.5f, 13f, 40f, 19f), size = 3f)
        zone(2, poly(67f, 22f, 66.5f, 13f, 60f, 19f), size = 3f)
        zone(1, circle(50f, 38f, 22f), label = Offset(50f, 26f))
        zone(3, oval(41f, 35f, 4.5f, 5.5f), size = 4f)
        zone(3, oval(59f, 35f, 4.5f, 5.5f), size = 4f)
        zone(2, poly(46.5f, 44f, 53.5f, 44f, 50f, 48.5f), size = 3f)
        line(50f, 48.5f, 50f, 52f, 46f, 54f)
        line(50f, 52f, 54f, 54f)
        line(44f, 47f, 30f, 45f)
        line(44f, 49f, 30f, 51f)
        line(56f, 47f, 70f, 45f)
        line(56f, 49f, 70f, 51f)
    },
    // Casa con árbol
    picture(5..5, listOf(YELLOW, RED, BROWN, LIGHT_BLUE, GREEN, GREY)) {
        zone(5, rect(0f, 85f, 100f, 100f), label = Offset(8f, 93f), size = 5f)
        zone(1, circle(86f, 15f, 8f), size = 5f)
        zone(3, rect(80f, 55f, 87f, 88f), label = Offset(83.5f, 76f), size = 4f)
        zone(5, circle(83.5f, 47f, 12f))
        zone(6, rect(54f, 16f, 61f, 36f), label = Offset(57.5f, 22f), size = 4f)
        zone(6, circle(60f, 10f, 3.5f), size = 3f)
        zone(6, circle(66f, 5f, 3.5f), size = 3f)
        zone(2, poly(6f, 45f, 37f, 14f, 68f, 45f), label = Offset(37f, 34f))
        zone(1, rect(12f, 44f, 62f, 88f), label = Offset(37f, 52f), size = 5f)
        zone(3, rect(31f, 62f, 43f, 88f), label = Offset(37f, 76f), size = 5f)
        zone(4, rect(16f, 54f, 27f, 65f), size = 4.5f)
        zone(4, rect(47f, 54f, 58f, 65f), size = 4.5f)
        line(21.5f, 54f, 21.5f, 65f)
        line(52.5f, 54f, 52.5f, 65f)
    },
)
