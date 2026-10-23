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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.TextoClaro
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import kotlin.math.min
import kotlin.random.Random

/**
 * Dibujo de cada palabra, en el mismo orden que el array `exercise_words` de cada
 * idioma (así la palabra va siempre con su dibujo, para quien no lea bien).
 */
private val WORD_PICTURES = listOf(
    "☀️", "🌙", "🏠", "🐱", "🐶", "🌸", "🍞", "🐟", "🍎", "⭐", "🚗", "🌳", "🥛", "☕", "🌊",
    "🍇", "🔑", "🛏️", "👁️", "✋", "🐄", "🐦", "🌹", "🧀", "🥚", "🚆", "⛵", "☁️", "🦆", "🐻"
)

/** Lado de la cuadrícula, número de palabras y largo máximo por nivel. */
private data class WordLevel(val size: Int, val words: Int, val maxLength: Int)

private val WORD_LEVELS = listOf(
    WordLevel(5, 3, 4), WordLevel(6, 4, 5), WordLevel(7, 5, 6), WordLevel(8, 6, 7), WordLevel(9, 7, 9)
)

/** Colores de las palabras encontradas. */
private val FOUND_COLORS = listOf(
    Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFFFCC80), Color(0xFFF48FB1),
    Color(0xFFCE93D8), Color(0xFFFFF59D), Color(0xFF80CBC4)
)

internal class PlacedWord(val picture: String, val word: String, val cells: List<Int>)

internal class WordGrid(val size: Int, val letters: List<Char>, val words: List<PlacedWord>)

/**
 * Coloca las palabras en horizontal (de izquierda a derecha) o en vertical (de arriba
 * abajo), pudiendo cruzarse en una letra común, y rellena el resto con letras del idioma.
 */
internal fun wordGrid(level: Int, entries: List<Pair<String, String>>, random: Random = Random.Default): WordGrid {
    val config = WORD_LEVELS[level - 1]
    val n = config.size
    // Relleno con letras sin tilde del idioma (las tildes sueltas despistan).
    val alphabet = entries.flatMap { it.second.toList() }
        .filter { it.isLetter() && Normalizer.normalize(it.toString(), Normalizer.Form.NFD).length == 1 }
        .distinct()
    var best: Pair<Array<Char?>, List<PlacedWord>>? = null
    for (round in 0 until 60) {
        val grid = arrayOfNulls<Char>(n * n)
        val placed = mutableListOf<PlacedWord>()
        for ((picture, word) in entries.filter { it.second.length in 3..min(config.maxLength, n) }.shuffled(random)) {
            if (placed.size == config.words) break
            if (placed.any { it.word == word }) continue
            for (attempt in 0 until 40) {
                val horizontal = random.nextBoolean()
                val r = random.nextInt(if (horizontal) n else n - word.length + 1)
                val c = random.nextInt(if (horizontal) n - word.length + 1 else n)
                val cells = word.indices.map { k -> if (horizontal) r * n + c + k else (r + k) * n + c }
                if (cells.withIndex().all { (k, cell) -> grid[cell] == null || grid[cell] == word[k] }) {
                    cells.forEachIndexed { k, cell -> grid[cell] = word[k] }
                    placed += PlacedWord(picture, word, cells)
                    break
                }
            }
        }
        if (placed.size > (best?.second?.size ?: -1)) best = grid to placed
        if (placed.size == config.words) break
    }
    val (grid, placed) = best!!
    return WordGrid(n, grid.map { it ?: alphabet.random(random) }, placed)
}

/**
 * Sopa de letras: arriba, las palabras con su dibujo; se marca una tocando su primera
 * letra y la última. Bien hecha si no hay más fallos que palabras.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordsExercise(level: Int, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val grid = remember {
        val locale = Locale.getDefault()
        val words = context.resources.getStringArray(R.array.exercise_words).map { it.trim().uppercase(locale) }
        wordGrid(level, WORD_PICTURES.zip(words))
    }
    val found = remember { mutableStateListOf<PlacedWord>() }
    var first by remember { mutableStateOf<Int?>(null) }
    var mistakes by remember { mutableIntStateOf(0) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val n = grid.size

    fun wrong() {
        mistakes++
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { shake.shake() }
    }

    fun tap(i: Int) {
        val start = first
        if (start == null || start == i) {
            first = if (start == i) null else i
            return
        }
        val (r1, c1) = start / n to start % n
        val (r2, c2) = i / n to i % n
        if (r1 != r2 && c1 != c2) {
            first = i // Otra fila y otra columna: empieza de nuevo desde aquí.
            return
        }
        first = null
        val cells = if (r1 == r2) (min(c1, c2)..maxOf(c1, c2)).map { r1 * n + it }
        else (min(r1, r2)..maxOf(r1, r2)).map { it * n + c1 }
        val word = grid.words.firstOrNull { it.cells == cells && it !in found }
        if (word == null) {
            wrong()
            return
        }
        found += word
        if (found.size == grid.words.size) onDone(mistakes <= grid.words.size)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            grid.words.forEach { word ->
                val index = found.indexOf(word)
                Row(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (index >= 0) FOUND_COLORS[index % FOUND_COLORS.size] else GrisBoton)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(word.picture, fontSize = 26.sp)
                    Text(
                        " " + word.word,
                        color = if (index >= 0) Color.Black else TextoClaro,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (index >= 0) TextDecoration.LineThrough else null
                    )
                }
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().shake(shake), contentAlignment = Alignment.Center) {
            val cell = min(maxWidth.value, maxHeight.value) / n
            val fontSize = with(LocalDensity.current) { (cell * 0.55f).dp.toSp() }
            Column {
                for (r in 0 until n) Row {
                    for (c in 0 until n) {
                        val i = r * n + c
                        val foundIndex = found.indexOfFirst { i in it.cells }
                        val background = when {
                            i == first -> Color(0xFFFFEB3B)
                            foundIndex >= 0 -> FOUND_COLORS[foundIndex % FOUND_COLORS.size]
                            else -> Color.White
                        }
                        Box(
                            Modifier
                                .size(cell.dp)
                                .border(0.5.dp, Color(0xFFB0BEC5))
                                .background(background)
                                .clickable { tap(i) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(grid.letters[i].toString(), color = Color.Black, fontSize = fontSize, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
