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

package com.launcherfacil.app.alerts

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.pills.PillStore
import com.launcherfacil.app.pills.formatPillCount
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.TileIcon
import com.launcherfacil.app.ui.VerdeLlamar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val AmarilloRayo = Color(0xFFFFD54F)

/** Batería baja: el móvil, con la batería en rojo, y el enchufe que entra y sale. */
@Composable
fun BatteryAlertScreen(percent: Int, onDone: () -> Unit) {
    // Al enchufar el cargador el aviso se quita solo. Solo cuenta si se enchufa con el aviso
    // ya abierto: el cuidador lo prueba a menudo con el móvil cargando y se cerraba al instante.
    val charging by BatteryWatcher.charging.collectAsState()
    var unplugged by remember { mutableStateOf(false) }
    LaunchedEffect(charging) {
        if (!charging) unplugged = true else if (unplugged) onDone()
    }
    Column(
        Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            ChargeDrawing(Modifier.fillMaxHeight(0.9f).aspectRatio(0.62f))
        }
        if (percent >= 0) {
            Text(
                "$percent %", color = RojoSos, fontSize = 64.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
        BigTile(VerdeLlamar, onDone, Modifier.fillMaxWidth().height(170.dp)) { TileIcon(R.drawable.ic_check, 0.6f) }
    }
}

@Composable
private fun ChargeDrawing(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "enchufe")
    val push by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "empuje")
    val blink by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "rayo")
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val line = w * 0.045f
        // Móvil.
        val phoneW = w * 0.62f
        val phoneH = h * 0.66f
        val phoneLeft = (w - phoneW) / 2
        drawRoundRect(Color.White, Offset(phoneLeft, 0f), Size(phoneW, phoneH), CornerRadius(w * 0.12f), style = Stroke(line))
        // Batería casi vacía, en rojo, con el rayo de "cargar".
        val batW = phoneW * 0.5f
        val batH = phoneH * 0.52f
        val batLeft = (w - batW) / 2
        val batTop = phoneH * 0.2f
        drawRoundRect(Color.White, Offset(batLeft + batW * 0.3f, batTop - batH * 0.08f), Size(batW * 0.4f, batH * 0.1f), CornerRadius(line))
        drawRoundRect(Color.White, Offset(batLeft, batTop), Size(batW, batH), CornerRadius(line * 1.2f), style = Stroke(line))
        drawRect(RojoSos, Offset(batLeft + line, batTop + batH * 0.8f), Size(batW - 2 * line, batH * 0.2f - line))
        bolt(Offset(w / 2, batTop + batH * 0.42f), batH * 0.5f, AmarilloRayo.copy(alpha = blink))
        // Enchufe que sube hacia el conector del móvil, con su cable.
        val plugW = w * 0.3f
        val plugH = h * 0.14f
        val gap = h * 0.1f * (1 - push)
        val plugTop = phoneH + line + h * 0.03f + gap
        val prongH = h * 0.05f
        drawRect(Color.White, Offset(w / 2 - plugW * 0.28f, plugTop - prongH), Size(plugW * 0.12f, prongH))
        drawRect(Color.White, Offset(w / 2 + plugW * 0.16f, plugTop - prongH), Size(plugW * 0.12f, prongH))
        drawRoundRect(Color.White, Offset(w / 2 - plugW / 2, plugTop), Size(plugW, plugH), CornerRadius(line))
        val cable = Path().apply {
            moveTo(w / 2, plugTop + plugH)
            cubicTo(w / 2, h * 0.98f, w * 0.15f, h * 0.9f, w * 0.05f, h)
        }
        drawPath(cable, Color.White, style = Stroke(line))
    }
}

private fun DrawScope.bolt(center: Offset, height: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x + height * 0.12f, center.y - height / 2)
        lineTo(center.x - height * 0.28f, center.y + height * 0.06f)
        lineTo(center.x - height * 0.02f, center.y + height * 0.06f)
        lineTo(center.x - height * 0.14f, center.y + height / 2)
        lineTo(center.x + height * 0.28f, center.y - height * 0.08f)
        lineTo(center.x + height * 0.02f, center.y - height * 0.08f)
        close()
    }
    drawPath(path, color)
}

/**
 * Las pastillas de una hora, una a una: la foto de la caja, cuántas (con dibujos y en
 * número) y el botón verde para decir que se ha tomado. Tras la última, un visto bueno.
 * El botón gris del reloj lo deja para luego: el aviso vuelve a los 15 minutos.
 */
@Composable
fun PillsAlertScreen(minutes: Int, version: Int, onDone: (answers: Map<String, Boolean>) -> Unit, onLater: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PillStore(context) }
    val doses = remember(minutes, version) { store.dosesAt(minutes) }
    var index by remember(minutes, version) { mutableIntStateOf(0) }
    var finished by remember(minutes, version) { mutableStateOf(false) }
    // Lo que ha contestado en cada caja: true = tomada, false = no se la toma.
    val answers = remember(minutes, version) { mutableStateListOf<Boolean>() }
    LaunchedEffect(finished) {
        if (finished) {
            delay(1500)
            onDone(doses.zip(answers).associate { (dose, taken) -> dose.id to taken })
        }
    }
    val answer = { taken: Boolean ->
        answers.add(taken)
        if (index < doses.lastIndex) index++ else finished = true
    }
    if (doses.isEmpty()) return

    Column(
        Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (finished) {
            // Visto bueno si se ha tomado alguna; si ha dicho que no a todas, una ✗ gris (sin regañar).
            val any = answers.any { it }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(260.dp).clip(CircleShape).background(if (any) VerdeLlamar else GrisBoton), contentAlignment = Alignment.Center) {
                    Icon(painterResource(if (any) R.drawable.ic_check else R.drawable.ic_close), null, tint = TextoClaro, modifier = Modifier.size(180.dp))
                }
            }
            return@Column
        }
        val dose = doses[index.coerceAtMost(doses.lastIndex)]
        // Puntos: cuántas cajas hay en esta toma y por cuál va.
        if (doses.size > 1) {
            Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                doses.indices.forEach { i ->
                    Box(
                        Modifier.size(22.dp).clip(CircleShape)
                            .background(
                                when {
                                    i < answers.size -> if (answers[i]) VerdeLlamar else RojoSos
                                    i == index -> TextoClaro
                                    else -> GrisBoton
                                }
                            )
                    )
                }
            }
        }
        val photo by produceState<Bitmap?>(null, dose.id) { value = withContext(Dispatchers.IO) { store.loadPhoto(dose.id) } }
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = photo
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else if (!store.hasPhoto(dose.id)) {
                PillShape(Modifier.fillMaxWidth(0.6f).aspectRatio(2.2f), half = false)
            }
        }
        Row(
            Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(24.dp)).background(Color.White).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillCount(dose.count, Modifier.weight(1f).fillMaxHeight())
            Text(formatPillCount(dose.count), color = Color(0xFF263238), fontSize = 64.sp, fontWeight = FontWeight.ExtraBold)
        }
        // Rojo = no me la tomo · gris = luego · verde (el más grande) = me la he tomado.
        Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(RojoSos, { answer(false) }, Modifier.weight(1f).fillMaxHeight()) { TileIcon(R.drawable.ic_close, 0.6f) }
            BigTile(GrisBoton, onLater, Modifier.weight(1f).fillMaxHeight()) { TileIcon(R.drawable.ic_snooze, 0.6f) }
            BigTile(VerdeLlamar, { answer(true) }, Modifier.weight(1.6f).fillMaxHeight()) { TileIcon(R.drawable.ic_check, 0.6f) }
        }
    }
}

/** Tantos dibujos de pastilla como haya que tomar (media pastilla = medio dibujo). */
@Composable
private fun PillCount(count: Float, modifier: Modifier) {
    val whole = count.toInt()
    val half = count - whole >= 0.5f
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(whole) { PillShape(Modifier.weight(1f, fill = false).height(56.dp).aspectRatio(1.8f), half = false) }
        if (half) PillShape(Modifier.weight(1f, fill = false).height(56.dp).aspectRatio(0.9f), half = true)
    }
}

/** Una cápsula de dos colores; la media es solo la mitad izquierda. */
@Composable
private fun PillShape(modifier: Modifier, half: Boolean) {
    val red = Color(0xFFE53935)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val r = CornerRadius(h / 2)
        if (half) {
            // Redondeada a la izquierda y cortada recta a la derecha.
            drawRoundRect(red, Offset.Zero, Size(w, h), r)
            drawRect(red, Offset(w - h / 2, 0f), Size(h / 2, h))
        } else {
            drawRoundRect(Color.White, Offset.Zero, Size(w, h), r)
            drawRoundRect(red, Offset.Zero, Size(w / 2 + h / 2, h), r)
            drawRect(Color.White, Offset(w / 2, 0f), Size(h / 2, h))
            drawRoundRect(Color(0xFF90A4AE), Offset.Zero, Size(w, h), r, style = Stroke(h * 0.06f))
        }
    }
}
