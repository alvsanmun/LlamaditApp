package com.launcherfacil.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val HOLD_MS = 2000

/** Botón SOS: hay que mantenerlo pulsado [HOLD_MS] para evitar activarlo sin querer. */
@Composable
fun SosButton(onSos: () -> Unit, modifier: Modifier = Modifier) {
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
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth(0.85f).aspectRatio(1f)) {
            val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            drawArc(TextoClaro.copy(alpha = 0.25f), 0f, 360f, false, style = stroke)
            drawArc(TextoClaro, -90f, 360f * progress.value, false, style = stroke)
        }
        Text("SOS", color = TextoClaro, fontSize = 52.sp, fontWeight = FontWeight.Black)
    }
}
