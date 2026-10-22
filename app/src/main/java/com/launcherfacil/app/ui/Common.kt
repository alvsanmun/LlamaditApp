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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.BackButton

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

/** Cómo se vuelve atrás en las pantallas (lo elige el cuidador). */
val LocalBackButton = staticCompositionLocalOf { BackButton.HOUSE_TOP }

/**
 * Barra superior con una casa grande para volver a la pantalla principal. Si el
 * cuidador ha elegido la flecha de abajo ([BackBar]), no se dibuja.
 */
@Composable
fun HomeBar(onHome: () -> Unit, modifier: Modifier = Modifier) {
    if (LocalBackButton.current != BackButton.HOUSE_TOP) return
    BigTile(GrisBoton, onHome, modifier.fillMaxWidth().height(88.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_home),
            contentDescription = null,
            tint = TextoClaro,
            modifier = Modifier.size(56.dp)
        )
    }
}

/**
 * Franja inferior con una flecha "atrás" fija abajo a la izquierda, para quien la
 * reconozca mejor que la casa. Hace lo mismo que el botón Atrás de Android: en un
 * mensaje abierto vuelve a la lista y en las demás pantallas, a la principal.
 */
@Composable
fun BackBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Fondo)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        BigTile(GrisBoton, onBack, Modifier.size(width = 140.dp, height = 96.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = null,
                tint = TextoClaro,
                modifier = Modifier.size(60.dp)
            )
        }
    }
}

/**
 * Un valor que se vuelve a calcular cada vez que la pantalla vuelve a primer plano
 * (por ejemplo, al regresar de los Ajustes de Android tras dar un permiso).
 */
@Composable
fun <T> rememberOnResume(read: () -> T): T {
    val owner = LocalLifecycleOwner.current
    var value by remember { mutableStateOf(read()) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) value = read()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return value
}
