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
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.BateriaMedia
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.HomeBar
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.VerdeLlamar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/**
 * Una partida de un ejercicio: barra con la casa, estrellas del nivel y el ejercicio.
 * Al terminar ([content] llama a su `onDone`) se apunta el resultado, se celebra
 * siempre (aunque haya habido fallos: es para animar) y empieza otra partida distinta.
 */
@Composable
internal fun ExerciseHost(
    exercise: Exercise,
    progress: ExerciseProgress,
    onHome: () -> Unit,
    content: @Composable (level: Int, onDone: (wellDone: Boolean) -> Unit) -> Unit
) {
    var level by remember { mutableIntStateOf(progress.level(exercise)) }
    var round by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun finish(wellDone: Boolean) {
        if (celebrating) return
        level = progress.record(exercise, wellDone)
        celebrating = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            delay(CELEBRATION_MS)
            celebrating = false
            round++
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Fondo)
                .safeDrawingPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeBar(onHome)
            LevelStars(level)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // Cada partida empieza de cero; el nivel se lee al empezarla.
                androidx.compose.runtime.key(round) {
                    val roundLevel = remember { level }
                    content(roundLevel, ::finish)
                }
            }
        }
        if (celebrating) Celebration()
    }
}

private const val CELEBRATION_MS = 1600L

/** Nivel del ejercicio como estrellas: tantas amarillas como el nivel. */
@Composable
internal fun LevelStars(level: Int, size: TextUnit = 26.sp, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(MAX_LEVEL) { i ->
            Text("★", color = if (i < level) BateriaMedia else GrisBoton, fontSize = size)
        }
    }
}

/** Visto bueno grande sobre la pantalla, con un pequeño rebote. */
@Composable
private fun Celebration() {
    val scale = remember { Animatable(0.3f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(240.dp)
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .background(VerdeLlamar, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                contentDescription = null,
                tint = TextoClaro,
                modifier = Modifier.size(170.dp)
            )
        }
    }
}

/** Sacudida lateral para un fallo (sin texto: se entiende como "no"). */
internal class ShakeState {
    val offset = Animatable(0f)

    suspend fun shake() {
        for (x in listOf(-28f, 28f, -20f, 20f, -10f, 10f, 0f)) offset.animateTo(x, tween(45))
    }
}

@Composable
internal fun rememberShake() = remember { ShakeState() }

internal fun Modifier.shake(state: ShakeState) = graphicsLayer { translationX = state.offset.value }

/** Texto (normalmente un emoji) que ocupa [fraction] del lado corto de su caja. */
@Composable
internal fun FitText(text: String, modifier: Modifier = Modifier, fraction: Float = 0.62f, color: Color = TextoClaro) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value)
        val size = with(LocalDensity.current) { (side * fraction).dp.toSp() }
        Text(text, fontSize = size, color = color, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}

/**
 * Voz del móvil (TextToSpeech) para "¿Quién es?": dice el nombre que hay que buscar.
 * Se abre al entrar en los ejercicios y se cierra al salir.
 */
internal object ExerciseVoice {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: String? = null

    fun start(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.getDefault()
                pending?.let { speak(it) }
            }
            pending = null
        }
    }

    fun speak(text: String) {
        val engine = tts
        if (engine == null || !ready) {
            pending = text
            return
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "exercise")
    }

    fun stop() {
        tts?.shutdown()
        tts = null
        ready = false
        pending = null
    }
}
