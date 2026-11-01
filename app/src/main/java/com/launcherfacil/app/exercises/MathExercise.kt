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

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
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

/** Con números pequeños se dibujan manzanas para poder contar (como mucho, estas). */
private const val MAX_OBJECTS = 6

/**
 * Cálculo con objetos (diseño "Cálculo con objetos"): la operación en una tarjeta
 * blanca, con manzanas debajo de cada número si son pequeños, un hueco con "?" y tres
 * respuestas grandes para elegir. Si falla, esa respuesta se apaga y puede probar otra.
 * Bien hecho si acierta a la primera.
 */
@Composable
internal fun MathExercise(level: Int, onDone: (Boolean) -> Unit) {
    val problem = remember { mathProblem(level) }
    val options = remember { choicesFor(problem.answer) }
    val wrong = remember { mutableStateListOf<Int>() }
    var solved by remember { mutableStateOf(false) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val objects = problem.op == '+' && problem.a <= MAX_OBJECTS && problem.b <= MAX_OBJECTS

    fun choose(value: Int) {
        if (solved || value in wrong) return
        if (value == problem.answer) {
            solved = true
            scope.launch {
                delay(600)
                onDone(wrong.isEmpty())
            }
            return
        }
        wrong += value
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch { shake.shake() }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(GmCard).padding(horizontal = 16.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Operand(problem.a, objects)
                Text(problem.op.toString(), color = GmBlue, fontSize = 52.sp, fontWeight = FontWeight.Black)
                Operand(problem.b, objects)
            }
            // Hueco de la respuesta: "?" en azul claro; al acertar, el número en verde.
            Box(
                Modifier
                    .size(width = 150.dp, height = 96.dp)
                    .shake(shake)
                    .drawBehind {
                        val w = 5.dp.toPx()
                        drawRoundRect(
                            color = if (solved) GmGreen else GmBlue,
                            cornerRadius = CornerRadius(24.dp.toPx()),
                            style = Stroke(w, pathEffect = if (solved) null else PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 9.dp.toPx())))
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (solved) problem.answer.toString() else "?",
                    color = if (solved) GmGreenDark else Color(0xFF90CAF9),
                    fontSize = 60.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
        Row(Modifier.fillMaxWidth().height(137.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEach { value ->
                val off = value in wrong
                RaisedBox(
                    if (off) GmBeige else GmBlue,
                    if (off) GmDashed else GmBlueDark,
                    Modifier.weight(1f).fillMaxSize(),
                    enabled = !off,
                    onClick = { choose(value) }
                ) {
                    FitText(value.toString(), Modifier.fillMaxSize(), fraction = when { value >= 100 -> 0.36f; value >= 10 -> 0.5f; else -> 0.6f }, color = if (off) GmDashed else Color.White)
                }
            }
        }
    }
}

/** Un número grande y, si cabe, tantas manzanas como vale. */
@Composable
private fun Operand(value: Int, objects: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (objects) {
            Column(Modifier.width(120.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                (0 until value).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { Image(painterResource(R.drawable.ic_apple), null, modifier = Modifier.size(54.dp)) }
                    }
                }
            }
        }
        Text(value.toString(), color = GmInk, fontSize = 52.sp, fontWeight = FontWeight.Black)
    }
}

/** La respuesta y dos parecidas (sin negativos), desordenadas. */
private fun choicesFor(answer: Int, random: Random = Random.Default): List<Int> {
    val near = (-3..3).map { answer + it }.filter { it >= 0 && it != answer }.shuffled(random).take(2)
    return (near + answer).shuffled(random)
}
