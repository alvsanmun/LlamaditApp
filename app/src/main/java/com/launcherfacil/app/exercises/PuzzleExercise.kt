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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.gohome.loadPortalPhoto
import com.launcherfacil.app.ui.BateriaAlta
import com.launcherfacil.app.ui.BateriaMedia
import kotlin.math.min

/** Piezas por lado según el nivel. */
private val PUZZLE_SIDES = listOf(2, 3, 4, 4, 5)

/** Dibujos para cuando no hay fotos de la familia ni del portal. */
private val PUZZLE_FALLBACK = listOf("🌻", "🐱", "🏠", "🚗", "🍎", "⛵", "🐶", "🌈")

/**
 * Puzle: una foto de la familia (o la del portal) partida en piezas desordenadas. Se
 * toca una pieza y luego otra para cambiarlas de sitio. Arriba se ve la foto entera;
 * en los niveles 1 a 3 las piezas bien colocadas se marcan en verde.
 * Bien hecho si no se usan muchos más cambios de los necesarios.
 */
@Composable
internal fun PuzzleExercise(level: Int, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null) { value = puzzleImage(context) }
    val picture = image ?: return
    val n = PUZZLE_SIDES[level - 1]
    // slots[i] = pieza que hay en el hueco i.
    val slots = remember {
        val list = (0 until n * n).toMutableList()
        do list.shuffle() while (list.withIndex().count { it.index == it.value } > n / 2)
        list.toMutableStateList()
    }
    var selected by remember { mutableStateOf<Int?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val hints = level <= 3

    fun tap(i: Int) {
        if (finished) return
        val first = selected
        if (first == null || first == i) {
            selected = if (first == i) null else i
            return
        }
        slots[first] = slots[i].also { slots[i] = slots[first] }
        selected = null
        moves++
        if (slots.withIndex().all { it.index == it.value }) {
            finished = true
            onDone(moves <= n * n)
        }
    }

    // Cuadrado central de la foto.
    val side = min(picture.width, picture.height)
    val left = (picture.width - side) / 2
    val top = (picture.height - side) / 2

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(110.dp).clip(RoundedCornerShape(12.dp))) {
            drawImage(
                picture,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(side, side),
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val board = min(maxWidth.value, maxHeight.value).dp
            Column(Modifier.size(board)) {
                for (r in 0 until n) Row(Modifier.weight(1f)) {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val piece = slots[i]
                        val border = when {
                            selected == i -> Modifier.border(5.dp, BateriaMedia)
                            hints && piece == i -> Modifier.border(3.dp, BateriaAlta)
                            else -> Modifier.border(1.dp, Color(0x88FFFFFF))
                        }
                        Canvas(
                            Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { tap(i) }
                                .then(border)
                        ) {
                            val pieceSide = side / n
                            drawImage(
                                picture,
                                srcOffset = IntOffset(left + piece % n * pieceSide, top + piece / n * pieceSide),
                                srcSize = IntSize(pieceSide, pieceSide),
                                dstSize = IntSize(size.width.toInt(), size.height.toInt())
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una foto al azar: favoritos con foto y el portal; si no hay, un dibujo. */
private suspend fun puzzleImage(context: Context): ImageBitmap {
    val sources: List<suspend () -> ImageBitmap?> =
        loadFavoriteContacts(context).map { contact -> suspend { loadContactPhoto(context, contact.id) } } +
            listOf(suspend { loadPortalPhoto(context)?.asImageBitmap() })
    for (source in sources.shuffled()) source()?.let { return it }
    return emojiPicture(PUZZLE_FALLBACK.random())
}

/** Un emoji grande sobre un degradado de colores. */
private fun emojiPicture(emoji: String): ImageBitmap {
    val size = 720
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val background = Paint().apply {
        shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF81D4FA.toInt(), 0xFFFFF59D.toInt(), Shader.TileMode.CLAMP)
    }
    canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), background)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * 0.7f
        textAlign = Paint.Align.CENTER
    }
    val y = size / 2f - (text.descent() + text.ascent()) / 2
    canvas.drawText(emoji, size / 2f, y, text)
    return bitmap.asImageBitmap()
}

private fun <T> List<T>.toMutableStateList() = mutableStateListOf<T>().also { it.addAll(this) }
