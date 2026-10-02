package com.launcherfacil.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R

/** Superficie grande, redondeada y con respuesta háptica al tocarla. */
@Composable
fun BigTile(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(color)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun TileIcon(@DrawableRes id: Int, fraction: Float = 0.55f) {
    Icon(
        painter = painterResource(id),
        contentDescription = null,
        tint = TextoClaro,
        modifier = Modifier.fillMaxSize(fraction)
    )
}

/** Barra superior con una casa grande para volver a la pantalla principal. */
@Composable
fun HomeBar(onHome: () -> Unit, modifier: Modifier = Modifier) {
    BigTile(GrisBoton, onHome, modifier.fillMaxWidth().height(88.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_home),
            contentDescription = null,
            tint = TextoClaro,
            modifier = Modifier.size(56.dp)
        )
    }
}
