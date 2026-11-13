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

package com.launcherfacil.app.camera

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.gohome.decodeScaled
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.ConfirmDelete
import com.launcherfacil.app.ui.EmptyIcon
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.HomeBar
import com.launcherfacil.app.ui.ListScaffold
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.TileIcon
import com.launcherfacil.app.ui.VioletaFotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fotos hechas con LlamaditApp: cuadrícula y, al tocar una, en grande con flechas y papelera. */
@Composable
fun PhotosScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photos by remember { mutableStateOf<List<Uri>?>(null) }
    var opened by remember { mutableStateOf<Uri?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { photos = loadPhotos(context) }

    val list = photos.orEmpty()
    val index = list.indexOf(opened)

    if (confirmDelete && opened != null) {
        BackHandler { confirmDelete = false }
        ConfirmDelete(
            onConfirm = {
                confirmDelete = false
                val photo = opened!!
                scope.launch {
                    deletePhoto(context, photo)
                    val updated = loadPhotos(context)
                    photos = updated
                    // Se queda en la siguiente foto; si era la última, vuelve a la cuadrícula.
                    opened = updated.getOrNull(index) ?: updated.getOrNull(index - 1)
                }
            },
            onCancel = { confirmDelete = false }
        )
        return
    }

    if (index >= 0) {
        BackHandler { opened = null }
        Column(
            modifier = Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeBar(onHome)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PhotoImage(list[index], 1600, ContentScale.Fit, Modifier.fillMaxSize())
            }
            Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ArrowTile(enabled = index > 0, reversed = false, Modifier.weight(1f)) { opened = list[index - 1] }
                BigTile(RojoSos, { confirmDelete = true }, Modifier.weight(1f).fillMaxSize()) {
                    TileIcon(R.drawable.ic_delete, 0.6f)
                }
                ArrowTile(enabled = index < list.lastIndex, reversed = true, Modifier.weight(1f)) { opened = list[index + 1] }
            }
        }
        return
    }

    ListScaffold(onHome) {
        when {
            photos == null -> Unit
            list.isEmpty() -> EmptyIcon(R.drawable.ic_photo_library, VioletaFotos)
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.toString() }) { photo ->
                    PhotoImage(
                        photo, 480, ContentScale.Crop,
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                            .background(GrisBoton).clickable { opened = photo }
                    )
                }
            }
        }
    }
}

/** Flecha para pasar de foto. Sin foto a ese lado no se dibuja, pero guarda el sitio. */
@Composable
private fun ArrowTile(enabled: Boolean, reversed: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (!enabled) {
        Spacer(modifier)
        return
    }
    BigTile(GrisBoton, onClick, modifier.fillMaxSize()) {
        Box(Modifier.rotate(if (reversed) 180f else 0f)) { TileIcon(R.drawable.ic_arrow_back, 0.6f) }
    }
}

@Composable
private fun PhotoImage(uri: Uri, maxSide: Int, scale: ContentScale, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri, maxSide) {
        value = withContext(Dispatchers.IO) {
            try {
                decodeScaled(context, uri, maxSide)
            } catch (e: Exception) {
                null
            }
        }
    }
    Box(modifier) {
        bitmap?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = scale) }
    }
}
