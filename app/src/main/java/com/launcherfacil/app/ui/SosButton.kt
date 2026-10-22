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

package com.launcherfacil.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.launcherfacil.app.R
import com.launcherfacil.app.config.SosIcon
import kotlinx.coroutines.launch

private const val HOLD_MS = 2000

/** Dibujo de cada opción del botón SOS (null = las letras "SOS"). */
@DrawableRes
fun SosIcon.drawable(): Int? = when (this) {
    SosIcon.TEXT -> null
    SosIcon.AMBULANCE -> R.drawable.ic_sos_ambulance
    SosIcon.CROSS -> R.drawable.ic_sos_cross
    SosIcon.SIREN -> R.drawable.ic_sos_siren
    SosIcon.HEART -> R.drawable.ic_sos_heart
    SosIcon.WARNING -> R.drawable.ic_sos_warning
}

/** Botón SOS: hay que mantenerlo pulsado [HOLD_MS] para evitar activarlo sin querer. */
@Composable
fun SosButton(onSos: () -> Unit, modifier: Modifier = Modifier, icon: SosIcon = SosIcon.TEXT) {
    val currentOnSos by rememberUpdatedState(onSos)
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(RojoSos)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val hold = scope.launch {
                        progress.animateTo(1f, tween(HOLD_MS, easing = LinearEasing))
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSos()
                    }
                    tryAwaitRelease()
                    if (progress.value < 1f) hold.cancel()
                    scope.launch { progress.animateTo(0f, tween(300)) }
                })
            }
    ) {
        SosFace(icon, progress.value)
    }
}

/**
 * Anillo de progreso con las letras o el dibujo dentro. Se ajusta al lado más corto
 * del botón, así cabe igual en un botón pequeño (bajo) que en uno grande.
 */
@Composable
fun BoxScope.SosFace(icon: SosIcon, progress: Float = 0f) {
    BoxWithConstraints(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight) * 0.85f
        val ring = (side.value * 0.075f).dp
        Canvas(Modifier.size(side)) {
            val stroke = Stroke(width = ring.toPx(), cap = StrokeCap.Round)
            val inset = stroke.width / 2
            val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = Offset(inset, inset)
            drawArc(TextoClaro.copy(alpha = 0.25f), 0f, 360f, false, topLeft, arcSize, style = stroke)
            drawArc(TextoClaro, -90f, 360f * progress, false, topLeft, arcSize, style = stroke)
        }
        val drawable = icon.drawable()
        if (drawable == null) {
            val fontSize = with(LocalDensity.current) { (side * 0.32f).toSp() }
            Text("SOS", color = TextoClaro, fontSize = fontSize, fontWeight = FontWeight.Black, softWrap = false)
        } else {
            Icon(painterResource(drawable), contentDescription = null, tint = TextoClaro, modifier = Modifier.size(side * 0.62f))
        }
    }
}
