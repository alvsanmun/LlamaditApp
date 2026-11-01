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

import android.app.Activity
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R

// Colores del panel del cuidador (diseño "Cuidador A/B"): claro, tarjetas blancas.
val CgBg = Color(0xFFF3F5F8)
val CgCard = Color(0xFFFFFFFF)
val CgInk = Color(0xFF1B2430)
val CgMuted = Color(0xFF5A6675)
val CgWarn = Color(0xFFA15C00)
val CgNavy = Color(0xFF1E3A5F)
val CgNeutral = Color(0xFFE8EDF2)
val CgBorder = Color(0xFFD5DCE4)
val CgChevron = Color(0xFF8A96A5)
val CgAccent = Color(0xFF1565C0)

/** Tema claro de Material para los controles del panel (interruptores, casillas…). */
@Composable
fun CaregiverTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = CgAccent,
            onPrimary = Color.White,
            surface = CgCard,
            onSurface = CgInk,
            background = CgBg,
            onBackground = CgInk,
            surfaceVariant = CgNeutral,
            onSurfaceVariant = CgMuted,
            outline = CgChevron
        ),
        content = {
            LightStatusBar()
            content()
        }
    )
}

/**
 * Iconos oscuros en la barra de estado mientras se ve una pantalla de fondo claro (el
 * panel del cuidador y los juegos); al salir se dejan como estaban (claros, sobre el
 * fondo oscuro del launcher).
 */
@Composable
fun LightStatusBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/**
 * Página del panel: botón cuadrado blanco arriba a la izquierda (casa en la portada,
 * flecha en los apartados), título grande y el contenido desplazable debajo.
 */
@Composable
fun CgPage(
    title: String,
    @DrawableRes navIcon: Int,
    onNav: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(CgBg).safeDrawingPadding()) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .clip(RoundedCornerShape(14.dp))
                    .background(CgCard)
                    .clickable(onClick = onNav),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(navIcon), contentDescription = null, tint = CgInk, modifier = Modifier.size(26.dp))
            }
            Text(title, color = CgInk, fontSize = 26.sp, fontWeight = FontWeight.Black)
        }
        header()
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

/** Tarjeta blanca con una sombra suave. */
@Composable
fun CgCard(modifier: Modifier = Modifier, padding: Int = 14, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CgCard)
            .padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

/** Etiqueta en mayúsculas sobre un grupo ("APARTADOS"). */
@Composable
fun CgLabel(text: String) {
    Text(
        text.uppercase(),
        color = CgMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
    )
}

/** Título de un bloque dentro de un apartado. */
@Composable
fun CgTitle(text: String) {
    Text(text, color = CgInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
}

@Composable
fun CgHint(text: String, color: Color = CgMuted) {
    Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp)
}

/** Aviso ámbar (algo que revisar, coste de los SMS…). */
@Composable
fun CgWarning(text: String) {
    Text("⚠ $text", color = CgWarn, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 21.sp)
}

/**
 * Fila de la portada: cuadrado de color con el dibujo, título, resumen del estado (en
 * ámbar si hay algo que revisar) y una flecha.
 */
@Composable
fun CgSectionRow(
    color: Color,
    @DrawableRes icon: Int,
    title: String,
    subtitle: String,
    warn: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .shadow(1.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(CgCard)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                subtitle,
                color = if (warn) CgWarn else CgMuted,
                fontSize = 13.sp,
                fontWeight = if (warn) FontWeight.Bold else FontWeight.SemiBold
            )
        }
        Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = CgChevron, modifier = Modifier.size(22.dp))
    }
}

/** Pastilla de color con texto (SOS, Vídeo, Ubicación, Sin foto). */
@Composable
fun CgTag(text: String, background: Color, foreground: Color = Color.White) {
    Text(
        text,
        color = foreground,
        fontSize = 12.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
fun CgSpacer() = Spacer(Modifier.size(4.dp))
