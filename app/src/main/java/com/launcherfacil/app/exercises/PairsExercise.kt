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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.ui.AzulTeclado
import com.launcherfacil.app.ui.BateriaAlta
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dibujos de las cartas: cosas cotidianas, fáciles de distinguir. */
private val PAIR_PICTURES = listOf(
    "🍎", "🍌", "🍇", "🍓", "🐶", "🐱", "🐟", "🐦", "🌻", "🌳", "☀️",
    "🌙", "⭐", "🚗", "🚲", "⚽", "🎈", "🎁", "🔔", "☂️", "🏠", "🥕"
)

/** Columnas × filas por nivel. Con 3 × 3 la casilla del centro queda vacía. */
private val PAIR_GRIDS = listOf(3 to 3, 3 to 4, 4 to 4, 4 to 5, 5 to 6)

/**
 * Parejas: cartas boca abajo; se levantan de dos en dos y, si son iguales, se quedan
 * a la vista. Bien hecha si no hay más fallos que parejas.
 */
@Composable
internal fun PairsExercise(level: Int, onDone: (Boolean) -> Unit) {
    val (cols, rows) = PAIR_GRIDS[level - 1]
    val pairs = cols * rows / 2
    val deck: List<String?> = remember {
        val cards: MutableList<String?> =
            PAIR_PICTURES.shuffled().take(pairs).flatMap { listOf(it, it) }.shuffled().toMutableList()
        if (cols * rows % 2 == 1) cards.add(cols * rows / 2, null)
        cards
    }
    val faceUp = remember { mutableStateListOf<Int>() }
    val matched = remember { mutableStateListOf<Int>() }
    var mistakes by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun tap(i: Int) {
        if (busy || deck[i] == null || i in matched || i in faceUp) return
        faceUp += i
        if (faceUp.size < 2) return
        val (a, b) = faceUp
        if (deck[a] == deck[b]) {
            matched += listOf(a, b)
            faceUp.clear()
            if (matched.size == pairs * 2) onDone(mistakes <= pairs)
        } else {
            mistakes++
            busy = true
            scope.launch {
                delay(1100)
                faceUp.clear()
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { r ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(cols) { c ->
                    val i = r * cols + c
                    val picture = deck[i]
                    if (picture == null) {
                        Box(Modifier.weight(1f))
                    } else {
                        Card(picture, i in faceUp || i in matched, i in matched, Modifier.weight(1f).fillMaxHeight()) {
                            tap(i)
                        }
                    }
                }
            }
        }
    }
}

/** Carta que gira al levantarse: azul por detrás, el dibujo sobre blanco por delante. */
@Composable
private fun Card(picture: String, shown: Boolean, matched: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (shown) 180f else 0f, tween(300), label = "card")
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clip(shape)
            .background(if (rotation > 90f) (if (matched) Color(0xFFE8F5E9) else Color.White) else AzulTeclado)
            .then(if (matched) Modifier.border(4.dp, BateriaAlta, shape) else Modifier)
            .clickable(onClick = onClick)
    ) {
        if (rotation > 90f) {
            // La cara se dibuja girada para que no salga del revés.
            FitText(picture, Modifier.fillMaxSize().graphicsLayer { rotationY = 180f })
        } else {
            FitText("?", Modifier.fillMaxSize(), fraction = 0.5f, color = Color(0x88FFFFFF))
        }
    }
}
