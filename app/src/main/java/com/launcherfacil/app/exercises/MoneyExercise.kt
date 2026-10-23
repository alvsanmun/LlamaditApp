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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.KeyBox
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.TileIcon
import com.launcherfacil.app.ui.VerdeLlamar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.random.Random

/** Cosas de la compra, para el precio. */
private val PRODUCTS = listOf("🍞", "🥛", "🍎", "🧀", "☕", "🍌", "🥚", "🐟", "🍅", "🥕", "🍪", "💐", "📰", "🍊")

/** Monedas y billetes de cada nivel (en céntimos) y precios posibles (mínimo, máximo, paso). */
private class MoneyLevel(val values: List<Int>, val min: Int, val max: Int, val step: Int)

private val MONEY_LEVELS = listOf(
    MoneyLevel(listOf(100, 200), 100, 500, 100),
    MoneyLevel(listOf(100, 200, 500), 300, 1000, 100),
    MoneyLevel(listOf(50, 100, 200, 500), 100, 1000, 50),
    MoneyLevel(listOf(10, 20, 50, 100, 200, 500, 1000), 100, 2000, 10),
    MoneyLevel(listOf(10, 20, 50, 100, 200, 500, 1000, 2000, 5000), 500, 5000, 10),
)

private const val MAX_TRIES = 3

/** Precio en euros a la manera del idioma ("3,50 €"); sin céntimos si es justo. */
internal fun formatEuros(cents: Int): String =
    NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
        currency = Currency.getInstance("EUR")
        if (cents % 100 == 0) {
            minimumFractionDigits = 0
            maximumFractionDigits = 0
        }
    }.format(cents / 100.0)

/**
 * Contar dinero: sale una cosa con su precio y hay que poner las monedas y billetes
 * justos. Se tocan abajo para añadirlos y arriba para quitarlos; el ✓ comprueba.
 * Al tercer fallo se enseña una forma de pagarlo. Bien hecha si se acierta a la primera.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MoneyExercise(level: Int, onDone: (Boolean) -> Unit) {
    val config = MONEY_LEVELS[level - 1]
    val product = remember { PRODUCTS.random() }
    val price = remember {
        config.min + Random.nextInt((config.max - config.min) / config.step + 1) * config.step
    }
    val wallet = remember { mutableStateListOf<Int>() }
    var mistakes by remember { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }
    val shake = rememberShake()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun check() {
        if (finished || wallet.isEmpty()) return
        if (wallet.sum() == price) {
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
                wallet.clear()
                wallet += payWith(price, config.values)
                delay(3000)
                onDone(false)
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Lo que hay que pagar.
        Row(
            Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(24.dp)).background(Color.White),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(product, fontSize = 60.sp)
            Text("  " + formatEuros(price), color = Color.Black, fontSize = 52.sp, fontWeight = FontWeight.Bold)
        }
        // Lo que se ha puesto (tocar para quitar).
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .shake(shake)
                .clip(RoundedCornerShape(24.dp))
                .background(if (finished) VerdeLlamar.copy(alpha = 0.5f) else Color(0xFF37474F))
                .padding(10.dp)
        ) {
            if (wallet.isEmpty()) {
                FitText("👛", Modifier.fillMaxSize(), fraction = 0.4f, color = Color.White.copy(alpha = 0.3f))
            }
            FlowRow(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                wallet.forEachIndexed { index, value ->
                    MoneyPiece(value, 64.dp) { if (!finished) wallet.removeAt(index) }
                }
            }
        }
        // Monedas y billetes para elegir.
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            config.values.forEach { value ->
                MoneyPiece(value, 76.dp) {
                    if (!finished && wallet.size < 30) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        wallet += value
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(88.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyBox(GrisBoton, { if (!finished) wallet.clear() }, Modifier.weight(1f)) {
                TileIcon(R.drawable.ic_backspace, 0.5f)
            }
            KeyBox(VerdeLlamar, ::check, Modifier.weight(2f)) { TileIcon(R.drawable.ic_check, 0.6f) }
        }
    }
}

/** Una forma de pagar el precio: de mayor a menor. */
private fun payWith(price: Int, values: List<Int>): List<Int> {
    var rest = price
    val out = mutableListOf<Int>()
    for (value in values.sortedDescending()) {
        while (rest >= value) {
            out += value
            rest -= value
        }
    }
    return out
}

private val GOLD = Color(0xFFD4A62A)
private val SILVER = Color(0xFFCFD8DC)

/**
 * Moneda o billete dibujado (no son imágenes de los reales): monedas doradas, el euro
 * y los dos euros con dos colores, y billetes con su color (5 gris, 10 rojo, 20 azul,
 * 50 naranja).
 */
@Composable
private fun MoneyPiece(cents: Int, size: Dp, onClick: () -> Unit) {
    val label = if (cents >= 100) "${cents / 100}€" else "$cents"
    if (cents >= 500) {
        val color = when (cents) {
            500 -> Color(0xFFB0BEC5)
            1000 -> Color(0xFFEF9A9A)
            2000 -> Color(0xFF90CAF9)
            else -> Color(0xFFFFCC80)
        }
        Box(
            Modifier
                .width(size * 1.7f)
                .height(size)
                .clip(RoundedCornerShape(8.dp))
                .background(color)
                .border(2.dp, Color(0x55000000), RoundedCornerShape(8.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(label, color = Color.Black, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Bold)
        }
        return
    }
    val diameter = size * when (cents) {
        10 -> 0.78f
        20 -> 0.86f
        50 -> 0.96f
        100 -> 0.92f
        else -> 1f
    }
    val (outer, inner) = when (cents) {
        100 -> SILVER to GOLD
        200 -> GOLD to SILVER
        else -> GOLD to GOLD
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(outer)
                .border(2.dp, Color(0x55000000), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(diameter * 0.72f).clip(CircleShape).background(inner))
            Text(label, color = Color.Black, fontSize = (diameter.value * 0.34f).sp, fontWeight = FontWeight.Bold)
        }
    }
}
