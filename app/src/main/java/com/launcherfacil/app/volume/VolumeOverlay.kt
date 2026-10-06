package com.launcherfacil.app.volume

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.VerdeLlamar
import kotlinx.coroutines.delay

private const val VISIBLE_MS = 2000L

/** Envuelve el contenido de una pantalla y le pone encima el panel grande de volumen. */
@Composable
fun WithVolumeOverlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        content()
        VolumeOverlay()
    }
}

/**
 * Panel grande de volumen: altavoz, una flecha que salta hacia arriba o hacia abajo
 * y una "escalera" de barras que se llenan. Desaparece a los 2 segundos.
 */
@Composable
private fun BoxScope.VolumeOverlay() {
    val change by VolumeController.change.collectAsState()
    var visible by remember { mutableStateOf(false) }
    val arrowOffset = remember { Animatable(0f) }

    LaunchedEffect(change?.id) {
        val current = change ?: return@LaunchedEffect
        visible = true
        // Salto de la flecha en la dirección del cambio.
        arrowOffset.snapTo(if (current.up) 40f else -40f)
        arrowOffset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        delay(VISIBLE_MS)
        visible = false
    }

    // Oscurece el resto de la pantalla para que solo destaque el panel.
    AnimatedVisibility(
        visible = visible && change != null,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = Modifier.matchParentSize()
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xB3000000)))
    }

    AnimatedVisibility(
        visible = visible && change != null,
        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.8f),
        exit = fadeOut(tween(300)) + scaleOut(targetScale = 0.9f),
        modifier = Modifier.align(Alignment.Center)
    ) {
        val current = change ?: return@AnimatedVisibility
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(36.dp))
                .background(Color(0xFF1E272E))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = null,
                    tint = TextoClaro,
                    modifier = Modifier.size(120.dp)
                )
                Spacer(Modifier.size(16.dp))
                Icon(
                    painter = painterResource(
                        if (current.up) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down
                    ),
                    contentDescription = null,
                    tint = if (current.up) VerdeLlamar else Color(0xFFFFB300),
                    modifier = Modifier
                        .size(120.dp)
                        .graphicsLayer { translationY = -arrowOffset.value }
                )
            }
            Spacer(Modifier.height(24.dp))
            VolumeStairs(current.level, current.max)
        }
    }
}

/** Barras de alturas crecientes; las de la izquierda, hasta el nivel actual, rellenas. */
@Composable
private fun VolumeStairs(level: Int, max: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().height(180.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (i in 1..max) {
            val color by animateColorAsState(
                if (i <= level) VerdeLlamar else GrisBoton,
                animationSpec = tween(200),
                label = "barra"
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(0.2f + 0.8f * i / max)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
    }
}
