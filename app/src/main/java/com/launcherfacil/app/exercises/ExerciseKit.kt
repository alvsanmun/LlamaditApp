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
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.BackButton
import com.launcherfacil.app.ui.LightStatusBar
import com.launcherfacil.app.ui.LocalBackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

// Colores de los juegos (diseños "Juegos"): fondo crema, tarjetas blancas, marrón para
// volver y verde/azul para el progreso.
internal val GmBg = Color(0xFFFFF6E9)
internal val GmInk = Color(0xFF2B1B12)
internal val GmCard = Color(0xFFFFFFFF)
internal val GmBrown = Color(0xFF5D4A3E)
internal val GmBrownDark = Color(0xFF3B2E26)
internal val GmBeige = Color(0xFFEADFD2)
internal val GmSand = Color(0xFFF1E8DC)
internal val GmDashed = Color(0xFFC9B5A0)
internal val GmGreen = Color(0xFF43A047)
internal val GmGreenDark = Color(0xFF2E7D32)
internal val GmGreenDeep = Color(0xFF1B4D1F)
internal val GmBlue = Color(0xFF1E88E5)
internal val GmBlueDark = Color(0xFF0D47A1)
internal val GmBlueRing = Color(0xFFBBDEFB)

/** Partidas seguidas de cada vez; al terminarlas sale la pantalla del premio. */
internal const val ROUNDS = 5

private const val CELEBRATION_MS = 900L

/**
 * Una tanda de [ROUNDS] partidas de un ejercicio (diseños "Juegos"): arriba el botón
 * marrón para volver y los puntos de la tanda (verde = hecha, azul = la de ahora); al
 * terminar cada partida, un visto bueno breve, y al terminar la tanda, el premio.
 */
@Composable
internal fun ExerciseHost(
    exercise: Exercise,
    progress: ExerciseProgress,
    onHome: () -> Unit,
    onMenu: () -> Unit,
    content: @Composable (level: Int, onDone: (wellDone: Boolean) -> Unit) -> Unit
) {
    LightStatusBar()
    var level by remember { mutableIntStateOf(progress.level(exercise)) }
    var round by remember { mutableIntStateOf(0) }
    val results = remember { mutableStateListOf<Boolean>() }
    var celebrating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun finish(wellDone: Boolean) {
        if (celebrating) return
        level = progress.record(exercise, wellDone)
        results += wellDone
        celebrating = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            delay(CELEBRATION_MS)
            celebrating = false
            round++
        }
    }

    if (results.size >= ROUNDS && !celebrating) {
        PrizeScreen(
            results = results.toList(),
            onAgain = {
                results.clear()
                round++
            },
            onMenu = onMenu
        )
        return
    }

    Box(Modifier.fillMaxSize().background(GmBg)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameNavButton(onHome, Modifier.size(76.dp))
                RoundDots(results.size, Modifier.weight(1f).height(76.dp))
            }
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

/** Botón marrón para volver: la casa o, si el cuidador eligió la flecha, la flecha. */
@Composable
internal fun GameNavButton(onClick: () -> Unit, modifier: Modifier) {
    val arrow = LocalBackButton.current == BackButton.ARROW_BOTTOM
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(GmBrown).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (arrow) R.drawable.ic_arrow_back else R.drawable.ic_game_home),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(40.dp)
        )
    }
}

/** Los puntos de la tanda: hechas en verde, la de ahora en azul con un anillo, el resto beis. */
@Composable
private fun RoundDots(done: Int, modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(22.dp)).background(GmCard),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(ROUNDS) { i ->
            when {
                i < done -> Box(Modifier.size(24.dp).clip(CircleShape).background(GmGreen))
                i == done -> Box(
                    Modifier.size(40.dp).clip(CircleShape).background(GmBlueRing),
                    contentAlignment = Alignment.Center
                ) { Box(Modifier.size(30.dp).clip(CircleShape).background(GmBlue)) }
                else -> Box(Modifier.size(24.dp).clip(CircleShape).background(GmBeige))
            }
        }
    }
}

/**
 * Botón con relieve (diseños "Juegos"): una base más oscura debajo que se ve como
 * sombra; al pulsarlo, el botón baja.
 */
@Composable
internal fun RaisedBox(
    color: Color,
    dark: Color,
    modifier: Modifier = Modifier,
    radius: Dp = 28.dp,
    depth: Dp = 7.dp,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape: Shape = RoundedCornerShape(radius)
    val drop = if (pressed && enabled) depth * 0.7f else 0.dp
    Box(modifier) {
        Box(Modifier.matchParentSize().padding(top = depth).clip(shape).background(dark))
        Box(
            Modifier
                .matchParentSize()
                .padding(bottom = depth)
                .offset(y = drop)
                .clip(shape)
                .background(color)
                .then(
                    if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) { content() }
    }
}

/** Visto bueno breve entre partida y partida: círculo verde con anillos que rebota. */
@Composable
private fun Celebration() {
    val scale = remember { Animatable(0.4f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    Box(Modifier.fillMaxSize().background(GmBg.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        CheckBadge(Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value })
    }
}

/** Círculo verde con el visto bueno y dos anillos claros alrededor. */
@Composable
private fun CheckBadge(modifier: Modifier = Modifier, size: Dp = 230.dp) {
    Box(modifier.size(size + 72.dp).clip(CircleShape).background(Color(0xFFE8F5E9)), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 36.dp).clip(CircleShape).background(Color(0xFFC8E6C9)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size).clip(CircleShape).background(GmGreenDark), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_game_check), null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
            }
        }
    }
}

/** Pegatinas del premio: una por partida bien hecha. */
private val STICKERS = listOf(
    Triple(R.drawable.ic_shape_star, Color(0xFFFFF3C4), Color(0xFFF9A825)),
    Triple(R.drawable.ic_shape_heart, Color(0xFFFFE0E0), Color(0xFFE53935)),
    Triple(R.drawable.ic_shape_drop, Color(0xFFE3F2FD), Color(0xFF1E88E5)),
    Triple(R.drawable.ic_shape_leaf, Color(0xFFE8F5E9), Color(0xFF2E7D32)),
    Triple(R.drawable.ic_shape_diamond, Color(0xFFF3E5F5), Color(0xFF8E44AD)),
)

/**
 * Premio al terminar la tanda (diseño "Premio al terminar"): confeti, el visto bueno
 * grande, los puntos de las partidas, las pegatinas ganadas y dos botones: otra vez
 * (verde) o volver a los juegos (marrón).
 */
@Composable
private fun PrizeScreen(results: List<Boolean>, onAgain: () -> Unit, onMenu: () -> Unit) {
    val stickers = results.count { it }.coerceAtMost(STICKERS.size)
    Box(Modifier.fillMaxSize().background(GmBg)) {
        Confetti()
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CheckBadge(size = 200.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    results.forEach { ok ->
                        Box(Modifier.size(28.dp).clip(CircleShape).background(if (ok) GmGreen else Color(0xFFFFB74D)))
                    }
                }
                if (stickers > 0) {
                    Row(
                        Modifier.clip(RoundedCornerShape(24.dp)).background(GmCard).padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(stickers) { i ->
                            val (icon, bg, fg) = STICKERS[i]
                            val last = i == stickers - 1
                            Box(
                                Modifier
                                    .size(if (last) 64.dp else 54.dp)
                                    .clip(CircleShape)
                                    .background(bg)
                                    .then(if (last) Modifier.border(4.dp, GmGreen, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(painterResource(icon), null, tint = fg, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                RaisedBox(GmGreenDark, GmGreenDeep, Modifier.weight(1f).fillMaxSize(), radius = 30.dp, onClick = onAgain) {
                    Icon(painterResource(R.drawable.ic_game_replay), null, tint = Color.White, modifier = Modifier.size(62.dp))
                }
                RaisedBox(GmBrown, GmBrownDark, Modifier.weight(1f).fillMaxSize(), radius = 30.dp, onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_game_grid), null, tint = Color.White, modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}

/** Trocitos de colores quietos alrededor del premio. */
@Composable
private fun Confetti() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val pieces = listOf(
            Triple(0.09f, 0.08f, Color(0xFFE53935)), Triple(0.86f, 0.14f, Color(0xFF1E88E5)),
            Triple(0.06f, 0.36f, Color(0xFFFBC02D)), Triple(0.88f, 0.40f, Color(0xFF8E44AD)),
            Triple(0.52f, 0.07f, Color(0xFF43A047)), Triple(0.20f, 0.20f, Color(0xFFEF6C00)),
            Triple(0.75f, 0.28f, Color(0xFFE53935)), Triple(0.30f, 0.50f, Color(0xFF00897B)),
        )
        pieces.forEachIndexed { i, (x, y, color) ->
            Box(
                Modifier
                    .offset(x = w * x, y = maxHeight * y)
                    .size(if (i % 2 == 0) 18.dp else 14.dp, if (i % 3 == 0) 26.dp else 16.dp)
                    .rotate(i * 35f)
                    .clip(RoundedCornerShape(if (i % 2 == 0) 4.dp else 8.dp))
                    .background(color)
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

/** Texto (un emoji o un número) que ocupa [fraction] del lado corto de su caja. */
@Composable
internal fun FitText(text: String, modifier: Modifier = Modifier, fraction: Float = 0.62f, color: Color = GmInk) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value)
        val size = with(LocalDensity.current) { (side * fraction).dp.toSp() }
        Text(text, fontSize = size, color = color, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
    }
}

/** Icono que ocupa [fraction] del lado corto de su caja. */
@Composable
internal fun FitIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, fraction: Float = 0.55f, tint: Color = Color.White) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth.value, maxHeight.value) * fraction
        Icon(painterResource(icon), null, tint = tint, modifier = Modifier.size(side.dp))
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
