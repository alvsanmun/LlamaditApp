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
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.radio.RadioBrowser
import com.launcherfacil.app.radio.RadioStations
import com.launcherfacil.app.radio.SLEEP_OPTIONS
import com.launcherfacil.app.radio.Station
import com.launcherfacil.app.radio.downloadLogo
import com.launcherfacil.app.radio.fetchBitmap
import com.launcherfacil.app.radio.loadLogo
import com.launcherfacil.app.radio.saveLogoFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Radio en el panel: aviso de que usa internet, las emisoras elegidas (orden, quitar y
 * cambiar el dibujo), el apagado automático y cómo añadir más: las más escuchadas del
 * país o buscando por nombre en radio-browser.info.
 */
@Composable
internal fun RadioSection() {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { RadioStations(context) }
    val scope = rememberCoroutineScope()
    // Cualquier cambio vuelve a leer lo guardado (y los logotipos).
    var version by remember { mutableIntStateOf(0) }
    val stations = remember(version) { store.stations }
    var sleep by remember { mutableIntStateOf(store.sleepMinutes) }

    Text("⚠ " + stringResource(R.string.radio_data_note), color = BateriaMedia, fontSize = 15.sp)

    // Cambiar el dibujo de una emisora (muchas no tienen logotipo en el directorio).
    var pickingFor by remember { mutableStateOf<String?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val uuid = pickingFor
        if (uri != null && uuid != null) scope.launch { if (saveLogoFrom(context, uuid, uri)) version++ }
    }

    if (stations.isEmpty()) {
        Text(stringResource(R.string.radio_no_stations), color = TextoSuave, fontSize = 16.sp)
    } else {
        Text(stringResource(R.string.radio_stations_hint), color = TextoSuave, fontSize = 16.sp)
    }
    stations.forEachIndexed { index, station ->
        val logo by produceState<Bitmap?>(null, station.uuid, version) { value = loadLogo(context, station.uuid) }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            StationThumb(station.name, logo, Modifier.clickable {
                pickingFor = station.uuid
                if (Kiosk.isLocked(activity)) activity.stopLockTask()
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            Text(
                station.name,
                color = TextoClaro,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            MoveButton(R.drawable.ic_arrow_up, index > 0) { move(store, index, -1); version++ }
            Spacer(Modifier.width(6.dp))
            MoveButton(R.drawable.ic_arrow_down, index < stations.lastIndex) { move(store, index, +1); version++ }
            Spacer(Modifier.width(6.dp))
            MoveButton(R.drawable.ic_close, true) { store.remove(station.uuid); version++ }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.radio_sleep_title), color = TextoClaro, fontSize = 18.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SLEEP_OPTIONS.forEach { minutes ->
            Chip(sleepLabel(minutes), selected = minutes == sleep) {
                sleep = minutes
                store.sleepMinutes = minutes
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.radio_add_title), color = TextoClaro, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Station>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    fun load(block: suspend () -> List<Station>?) {
        loading = true
        failed = false
        scope.launch {
            val found = block()
            loading = false
            failed = found == null
            results = found
        }
    }
    ActionButton(stringResource(R.string.radio_top), { load { RadioBrowser.top(context) } })
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        label = { Text(stringResource(R.string.radio_search_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) load { RadioBrowser.byName(query) } }),
        modifier = Modifier.fillMaxWidth()
    )
    ActionButton(stringResource(R.string.radio_search), { if (query.isNotBlank()) load { RadioBrowser.byName(query) } })

    when {
        loading -> CircularProgressIndicator(Modifier.padding(8.dp))
        failed -> Text("⚠ " + stringResource(R.string.radio_no_connection), color = BateriaMedia, fontSize = 15.sp)
        results?.isEmpty() == true -> Text(stringResource(R.string.radio_no_results), color = TextoSuave, fontSize = 16.sp)
    }
    results?.forEach { station ->
        val added = stations.any { it.uuid == station.uuid }
        val logo by produceState<Bitmap?>(null, station.favicon) {
            value = if (station.favicon.isBlank()) null else withContext(Dispatchers.IO) { fetchBitmap(station.favicon) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            StationThumb(station.name, logo)
            Text(
                station.name,
                color = TextoClaro,
                fontSize = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            @DrawableRes val icon = if (added) R.drawable.ic_check else R.drawable.ic_add
            MoveButton(icon, !added) {
                store.add(station)
                version++
                scope.launch { if (downloadLogo(context, station)) version++ }
            }
        }
    }
}

private fun move(store: RadioStations, index: Int, by: Int) {
    val list = store.stations.toMutableList()
    val target = index + by
    if (target !in list.indices) return
    list.add(target, list.removeAt(index))
    store.stations = list
}

@Composable
private fun sleepLabel(minutes: Int): String = when {
    minutes == 0 -> stringResource(R.string.radio_sleep_never)
    minutes < 60 -> stringResource(R.string.radio_sleep_minutes, minutes)
    else -> stringResource(R.string.radio_sleep_hours, minutes / 60)
}

/** Logotipo pequeño (o el color con la inicial, como en la pantalla de la radio). */
@Composable
private fun StationThumb(name: String, logo: Bitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (logo != null) Color.White else stationColor(name)),
        contentAlignment = Alignment.Center
    ) {
        if (logo != null) {
            Image(logo.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
        } else {
            Text(name.take(1).uppercase(), color = TextoClaro, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color(0xFF90CAF9) else GrisBoton)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) Color.Black else TextoClaro, fontSize = 15.sp)
    }
}
