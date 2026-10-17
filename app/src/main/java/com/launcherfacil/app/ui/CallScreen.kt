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
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R

/**
 * Pantalla de llamada (normal o de WhatsApp): la foto de quien llama ocupando
 * media pantalla y, abajo, botones enormes. [caption] es el nombre o el número
 * (pequeño, para quien sepa leer); [status] la duración de la llamada.
 */
@Composable
fun CallLayout(
    photo: ImageBitmap?,
    caption: String,
    status: String?,
    isVideo: Boolean,
    buttons: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(36.dp))
                    .background(if (isVideo) VerdeWhatsApp else VerdeLlamar),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    Image(
                        bitmap = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        tint = TextoClaro,
                        modifier = Modifier.fillMaxSize(0.75f)
                    )
                }
                if (isVideo) {
                    // Cámara en la esquina: es una videollamada.
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(14.dp)
                            .size(72.dp)
                            .background(AzulTeclado, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_videocam),
                            contentDescription = null,
                            tint = TextoClaro,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }
        Text(
            caption,
            color = TextoClaro,
            fontSize = 30.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            status.orEmpty(),
            color = TextoSuave,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.height(44.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            content = buttons
        )
    }
}

/** Botón enorme de la pantalla de llamada. */
@Composable
fun RowScope.CallButton(
    color: Color,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    weight: Float = 1f,
    pulse: Boolean = false
) {
    // El botón de descolgar "late" para llamar la atención.
    val scale = if (pulse) {
        val transition = rememberInfiniteTransition(label = "pulso")
        val value by transition.animateFloat(
            initialValue = 0.92f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "escala"
        )
        value
    } else 1f
    BigTile(color, onClick, Modifier.weight(weight).fillMaxHeight().scale(scale)) {
        TileIcon(icon, 0.5f)
    }
}

/** Duración en formato m:ss o h:mm:ss. */
fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
