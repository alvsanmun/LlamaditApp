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
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.gohome.addressOf
import com.launcherfacil.app.gohome.cameraUri
import com.launcherfacil.app.gohome.deletePortalPhoto
import com.launcherfacil.app.gohome.loadPortalPhoto
import com.launcherfacil.app.gohome.savePortalPhoto
import com.launcherfacil.app.gohome.positionOf
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.sos.currentLocation
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Casa de la persona: se marca con la ubicación actual (el cuidador, en la casa) o
 * buscando la dirección. La dirección se puede corregir a mano (piso, puerta…).
 */
@Composable
fun HouseEditor(onOpenPin: (Double, Double) -> Unit, onTestRoute: (Double, Double) -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    val scope = rememberCoroutineScope()
    var position by remember { mutableStateOf(settings.housePosition) }
    var address by remember { mutableStateOf(settings.houseAddress) }
    var status by remember { mutableStateOf<Int?>(null) }

    fun save(newPosition: Pair<Double, Double>, newAddress: String? = null) {
        position = newPosition
        settings.housePosition = newPosition
        if (newAddress != null) {
            address = newAddress
            settings.houseAddress = newAddress
        }
        Kiosk.applyMaps(context)
    }

    Text(
        position?.let { stringResource(R.string.settings_house_saved, "%.5f, %.5f".format(Locale.ROOT, it.first, it.second)) }
            ?: stringResource(R.string.settings_house_none),
        color = if (position != null) TextoClaro else TextoSuave,
        fontSize = 16.sp
    )
    status?.let { Text(stringResource(it), color = BateriaMedia, fontSize = 16.sp) }

    ActionButton(stringResource(R.string.settings_house_use_location), {
        status = R.string.settings_house_searching
        scope.launch {
            val location = currentLocation(context)
            if (location == null) {
                status = R.string.settings_house_no_location
                return@launch
            }
            // La dirección se rellena sola si hay internet; si no, se escribe a mano.
            val found = addressOf(context, location.latitude, location.longitude)
            save(location.latitude to location.longitude, found)
            status = if (found == null) R.string.settings_house_no_address else null
        }
    })

    OutlinedTextField(
        value = address,
        onValueChange = {
            address = it
            settings.houseAddress = it
        },
        label = { Text(stringResource(R.string.settings_house_address)) },
        supportingText = { Text(stringResource(R.string.settings_house_address_hint)) },
        modifier = Modifier.fillMaxWidth()
    )

    ActionButton(stringResource(R.string.settings_house_search), {
        if (address.isBlank()) return@ActionButton
        status = R.string.settings_house_searching
        scope.launch {
            val found = positionOf(context, address)
            if (found == null) {
                status = R.string.settings_house_not_found
            } else {
                save(found)
                status = null
            }
        }
    })

    // Foto del portal: se enseña al llegar a casa siguiendo la ruta.
    PortalPhotoEditor()

    // Aviso al cuidador cuando la persona pulsa el botón.
    var alert by remember { mutableStateOf(settings.goHomeAlert) }
    SmsSwitch(stringResource(R.string.settings_house_alert), alert) {
        alert = it
        settings.goHomeAlert = it
    }
    Text(stringResource(R.string.settings_house_alert_hint), color = TextoSuave, fontSize = 16.sp)

    // Ubicación cada 10 minutos durante la ruta, hasta que llegue.
    var updates by remember { mutableStateOf(settings.goHomeUpdates) }
    SmsSwitch(stringResource(R.string.settings_house_updates), updates) {
        updates = it
        settings.goHomeUpdates = it
    }
    Text(stringResource(R.string.settings_house_updates_hint), color = TextoSuave, fontSize = 16.sp)
    SmsCostNote()

    position?.let { (lat, lng) ->
        // El cuidador acepta aquí las primeras pantallas de Maps (bienvenida, condiciones
        // de la navegación), para que la persona no se las encuentre.
        ActionButton(stringResource(R.string.settings_house_test_route), { onTestRoute(lat, lng) })
        ActionButton(stringResource(R.string.settings_house_view_map), { onOpenPin(lat, lng) })
    }
}

/**
 * Foto del portal: hacerla con la cámara o elegirla de la galería. Las dos son otras
 * apps, así que se sale un momento del quiosco (vuelve solo al regresar).
 */
@Composable
private fun PortalPhotoEditor() {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val photo by produceState<Bitmap?>(null, version) { value = loadPortalPhoto(context) }
    var failed by remember { mutableStateOf(false) }

    fun save(uri: Uri) = scope.launch {
        failed = !savePortalPhoto(context, uri)
        version++
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) save(cameraUri(context))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) save(uri)
    }
    fun leaveKiosk() {
        if (Kiosk.isLocked(activity)) activity.stopLockTask()
    }

    Text(stringResource(R.string.settings_house_photo), color = TextoClaro, fontSize = 18.sp)
    photo?.let {
        Image(
            it.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
        )
    }
    if (failed) Text(stringResource(R.string.settings_house_photo_failed), color = BateriaMedia, fontSize = 16.sp)
    ActionButton(stringResource(R.string.settings_house_photo_camera), {
        leaveKiosk()
        try {
            camera.launch(cameraUri(context))
        } catch (e: ActivityNotFoundException) {
            failed = true
        }
    })
    ActionButton(stringResource(R.string.settings_house_photo_gallery), {
        leaveKiosk()
        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    })
    if (photo != null) {
        ActionButton(stringResource(R.string.settings_house_photo_remove), {
            deletePortalPhoto(context)
            version++
        })
    }
}
