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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.radio.Radio
import com.launcherfacil.app.radio.RadioStations
import com.launcherfacil.app.radio.Station
import com.launcherfacil.app.radio.loadLogo

/**
 * Botón "Radio": los logotipos de las emisoras que ha elegido el cuidador. Tocar uno
 * la pone (sigue sonando al salir y con la pantalla apagada); la que suena "late" y
 * lleva un marco verde. El botón rojo de abajo la apaga.
 */
@Composable
fun RadioScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val stations = remember { RadioStations(context).stations }
    val state by Radio.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        HomeBar(onHome)
        Spacer(Modifier.height(16.dp))
        if (stations.isEmpty()) {
            CaregiverMessage(stringResource(R.string.radio_none))
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(stations, key = { it.uuid }) { station ->
                val playing = state.playing == station.uuid
                StationTile(
                    station,
                    playing = playing,
                    buffering = playing && state.buffering,
                    failed = state.failed == station.uuid,
                    onClick = { if (!playing) Radio.play(context, station.uuid) }
                )
            }
        }
        if (state.playing != null) {
            BigTile(RojoSos, Radio::stop, Modifier.fillMaxWidth().height(120.dp)) {
                TileIcon(R.drawable.ic_stop, 0.6f)
            }
        }
    }
}

@Composable
private fun StationTile(station: Station, playing: Boolean, buffering: Boolean, failed: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val logo by produceState<android.graphics.Bitmap?>(null, station.uuid) { value = loadLogo(context, station.uuid) }
    val shape = RoundedCornerShape(28.dp)
    val frame = when {
        playing -> Modifier.border(8.dp, BateriaAlta, shape)
        failed -> Modifier.border(8.dp, RojoSos, shape)
        else -> Modifier
    }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).pulsing(playing && !buffering)) {
        BigTile(if (logo != null) Color.White else stationColor(station.name), onClick, Modifier.fillMaxSize().then(frame)) {
            val bitmap = logo
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    contentDescription = station.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(20.dp)
                )
            } else {
                // Sin logotipo: el nombre en grande sobre un color fijo para cada emisora.
                Text(
                    station.name,
                    color = TextoClaro,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(12.dp)
                )
            }
            if (buffering) CircularProgressIndicator(color = BateriaAlta, strokeWidth = 8.dp, modifier = Modifier.size(72.dp))
        }
        // Esquina: altavoz verde = suena; aspa roja = no se ha podido poner.
        if (playing || failed) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .background(if (playing) BateriaAlta else RojoSos, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(if (playing) R.drawable.ic_volume_up else R.drawable.ic_close),
                    contentDescription = null,
                    tint = TextoClaro,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

/** Color fijo para una emisora sin logotipo (siempre el mismo para el mismo nombre). */
internal fun stationColor(name: String): Color {
    val colors = listOf(AnilRadio, VerdeWhatsApp, MarronCasa, RosaEjercicios, NaranjaMensajes, MoradoPerdidas)
    return colors[Math.floorMod(name.hashCode(), colors.size)]
}
