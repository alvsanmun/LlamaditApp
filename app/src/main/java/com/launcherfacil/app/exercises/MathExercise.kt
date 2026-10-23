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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.BateriaMedia
import com.launcherfacil.app.ui.DigitKey
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.KeyBox
import com.launcherfacil.app.ui.KeyRow
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.TileIcon
import com.launcherfacil.app.ui.VerdeLlamar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Una operación sencilla, con su resultado (nunca negativo ni con decimales). */
internal data class MathProblem(val a: Int, val op: Char, val b: Int) {
    val answer: Int
        get() = when (op) {
            '+' -> a + b
            '−' -> a - b
            '×' -> a * b
            else -> a / b
        }
}

/**
 * Operaciones según el nivel:
 * 1. sumas hasta 10 (con puntos para contar);
 * 2. sumas y restas hasta 20;
 * 3. sumas y restas hasta 50 y multiplicaciones hasta 5 × 5;
 * 4. sumas y restas hasta 100, multiplicaciones hasta 10 × 5 y divisiones exactas entre 2 y 5;
 * 5. lo mismo con multiplicaciones y divisiones hasta 10.
 */
internal fun mathProblem(level: Int, random: Random = Random.Default): MathProblem {
    val ops = when (level) {
        1 -> "+"
        2 -> "+−"
        3 -> "+−×"
        else -> "+−×÷"
    }
    val max = listOf(10, 20, 50, 100, 100)[level - 1]
    return when (ops.random(random)) {
        '+' -> {
            val a = random.nextInt(1, max)
            MathProblem(a, '+', random.nextInt(1, max - a + 1))
        }
        '−' -> {
            val a = random.nextInt(2, max + 1)
            MathProblem(a, '−', random.nextInt(1, a))
        }
        '×' -> {
            val (maxA, maxB) = when (level) {
                3 -> 5 to 5
                4 -> 10 to 5
                else -> 10 to 10
            }
            MathProblem(random.nextInt(1, maxA + 1), '×', random.nextInt(1, maxB + 1))
        }
        else -> {
            val limit = if (level == 4) 5 else 10
            val b = random.nextInt(2, limit + 1)
            MathProblem(b * random.nextInt(1, limit + 1), '÷', b)
        }
    }
}

private const val MAX_TRIES = 3

/**
 * Cálculo: la operación en grande y un teclado como el de una calculadora. Si falla,
 * se sacude y puede volver a intentarlo; al tercer fallo se enseña el resultado.
 */
@Composable
internal fun MathExercise(level: Int, onDone: (Boolean) -> Unit) {
    val problem = remember { mathProblem(level) }
    var input by remember { mutableStateOf("") }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun check() {
        if (input.isEmpty() || finished) return
        if (input.toInt() == problem.answer) {
            finished = true
            onDone(mistakes == 0)
            return
        }
        mistakes++
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            shake.shake()
            if (mistakes >= MAX_TRIES) {
                finished = true
                input = problem.answer.toString()
                delay(2500)
                onDone(false)
            } else {
                input = ""
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${problem.a} ${problem.op} ${problem.b}",
                color = TextoClaro,
                fontSize = 76.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            // Nivel 1: puntos para poder contar con el dedo.
            if (level == 1) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Dots(problem.a)
                    Text("  +  ", color = TextoClaro, fontSize = 28.sp)
                    Dots(problem.b)
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .shake(shake)
                .clip(RoundedCornerShape(24.dp))
                .background(if (finished) VerdeLlamar else GrisBoton),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "= " + input.ifEmpty { "?" },
                color = TextoClaro,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Column(Modifier.weight(2.2f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            fun type(digit: String) {
                if (!finished && input.length < 3) input = (input + digit).trimStart('0').ifEmpty { "0" }
            }
            for (row in listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))) {
                KeyRow { row.forEach { DigitKey(it, Modifier.weight(1f)) { type(it) } } }
            }
            KeyRow {
                KeyBox(
                    color = GrisBoton,
                    onClick = { if (!finished) input = input.dropLast(1) },
                    modifier = Modifier.weight(1f)
                ) { TileIcon(R.drawable.ic_backspace, 0.5f) }
                DigitKey("0", Modifier.weight(1f)) { type("0") }
                KeyBox(color = VerdeLlamar, onClick = ::check, modifier = Modifier.weight(1f)) {
                    TileIcon(R.drawable.ic_check, 0.6f)
                }
            }
        }
    }
}

/** Hasta 10 puntos, en filas de 5. */
@Composable
private fun Dots(count: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        (0 until count).chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { Box(Modifier.size(20.dp).background(BateriaMedia, CircleShape)) }
            }
        }
    }
}
