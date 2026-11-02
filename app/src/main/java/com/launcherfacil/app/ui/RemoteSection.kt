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

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.gohome.cameraUri
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.remote.Remote
import com.launcherfacil.app.remote.readQr
import kotlinx.coroutines.launch

private const val QR_FILE = "qr.jpg"

/** Resumen de la portada para el apartado "Gestión a distancia". */
@Composable
internal fun remoteSummary(context: Context): Pair<String, Boolean> {
    val caregivers by Remote.caregivers.collectAsState()
    val status by Remote.status.collectAsState()
    return when {
        !Remote.isEnabled(context) -> stringResource(R.string.cg_off) to false
        status == Remote.Status.ERROR -> stringResource(R.string.cg_remote_status_error) to true
        caregivers.isEmpty() -> stringResource(R.string.cg_remote_sub_none) to true
        else -> stringResource(R.string.cg_remote_sub_on, caregivers.size) to false
    }
}

/**
 * Gestión a distancia. Cada familia usa su propio proyecto de Firebase: el móvil se
 * conecta haciendo una foto al código QR que enseña la web del cuidador, que lleva los
 * datos del proyecto y una invitación. Aquí también el nombre con el que sale la
 * persona, la lista de cuidadores vinculados y desconectar.
 */
@Composable
internal fun RemotePage(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()
    val status by Remote.status.collectAsState()
    val caregivers by Remote.caregivers.collectAsState()
    // Se relee el proyecto tras conectar o desconectar.
    var version by remember { mutableIntStateOf(0) }
    val project = remember(version) { Remote.project(context) }
    var name by remember { mutableStateOf(Remote.deviceName(context)) }
    var unlink by remember { mutableStateOf<Remote.Caregiver?>(null) }
    var askDisconnect by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // Resultado de la última foto: texto y si es un aviso.
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    // QR de otro proyecto, pendiente de confirmar el cambio: (proyecto, texto del QR).
    var otherProject by remember { mutableStateOf<Pair<String, String>?>(null) }
    val resumed = rememberOnResume { System.nanoTime() }
    val batteryFree = remember(resumed) {
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    }

    val noName = stringResource(R.string.cg_remote_noname)
    val invalidText = stringResource(R.string.cg_remote_scan_invalid)
    val expiredText = stringResource(R.string.cg_remote_scan_expired)
    val failedText = stringResource(R.string.cg_remote_scan_failed)

    fun link(text: String, switch: Boolean = false) = scope.launch {
        busy = true
        message = null
        val result = Remote.linkFromQr(context, text, switch)
        busy = false
        version++
        message = when (result) {
            is Remote.LinkResult.Linked ->
                "✓ " + context.getString(R.string.cg_remote_linked_ok, result.name.ifBlank { noName }) to false
            is Remote.LinkResult.OtherProject -> {
                otherProject = result.projectId to text
                null
            }
            Remote.LinkResult.Invalid -> invalidText to true
            Remote.LinkResult.Expired -> expiredText to true
            Remote.LinkResult.Failed -> failedText to true
        }
    }
    fun gotPhoto(uri: Uri) = scope.launch {
        busy = true
        message = null
        val text = readQr(context, uri)
        if (text == null) {
            busy = false
            message = invalidText to true
        } else {
            link(text)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) gotPhoto(cameraUri(context, QR_FILE))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) gotPhoto(uri)
    }
    // La cámara y la galería son otras apps: se sale un momento del quiosco.
    fun leaveKiosk() {
        if (Kiosk.isLocked(activity)) activity.stopLockTask()
    }

    @Composable
    fun ScanButtons() {
        if (busy) {
            Text(stringResource(R.string.cg_remote_status_connecting), color = CgMuted, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            return
        }
        ActionButton(stringResource(R.string.cg_remote_scan_camera), {
            leaveKiosk()
            camera.launch(cameraUri(context, QR_FILE))
        }, color = CgAccent)
        ActionButton(stringResource(R.string.cg_remote_scan_gallery), {
            leaveKiosk()
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        message?.let { (text, warn) ->
            Text(text, color = if (warn) CgWarn else Color(0xFF2E7D32), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    CgPage(stringResource(R.string.cg_remote), R.drawable.ic_chevron_left, onBack) {
        CgHint(stringResource(R.string.cg_remote_hint))

        if (project == null) {
            CgCard {
                CgHint(stringResource(R.string.cg_remote_setup_hint), color = CgInk)
                CgHint(stringResource(R.string.cg_remote_guide, stringResource(R.string.remote_guide_url)))
                ScanButtons()
                CgWarning(stringResource(R.string.cg_remote_data_note))
            }
            return@CgPage
        }

        CgCard {
            val (text, color) = when (status) {
                Remote.Status.ONLINE -> stringResource(R.string.cg_remote_status_online) to Color(0xFF2E7D32)
                Remote.Status.ERROR -> stringResource(R.string.cg_remote_status_error) to CgWarn
                else -> stringResource(R.string.cg_remote_status_connecting) to CgMuted
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text("  $text", color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            CgHint(stringResource(R.string.cg_remote_project, project.projectId))
            CgWarning(stringResource(R.string.cg_remote_data_note))
        }

        CgCard {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    Remote.setDeviceName(context, it)
                },
                label = { Text(stringResource(R.string.cg_remote_name)) },
                supportingText = { Text(stringResource(R.string.cg_remote_name_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!batteryFree) {
            CgCard {
                CgWarning(stringResource(R.string.cg_remote_battery_warn))
                ActionButton(stringResource(R.string.cg_remote_battery_button), {
                    @SuppressLint("BatteryLife")
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    Kiosk.pauseAndOpen(activity, intent)
                })
            }
        }

        CgLabel(stringResource(R.string.cg_remote_linked_title))
        CgCard {
            if (caregivers.isEmpty()) CgHint(stringResource(R.string.cg_remote_linked_none))
            caregivers.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        c.name.ifBlank { noName },
                        color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { unlink = c }) {
                        Text(stringResource(R.string.cg_remote_unlink), color = RojoSos, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }

        CgLabel(stringResource(R.string.cg_remote_link))
        CgCard {
            CgHint(stringResource(R.string.cg_remote_link_hint))
            ScanButtons()
        }

        ActionButton(stringResource(R.string.cg_remote_disconnect), { askDisconnect = true }, color = RojoSos)
    }

    unlink?.let { c ->
        AlertDialog(
            onDismissRequest = { unlink = null },
            text = { Text(stringResource(R.string.cg_remote_unlink_confirm, c.name.ifBlank { noName })) },
            confirmButton = {
                TextButton(onClick = {
                    unlink = null
                    scope.launch { Remote.removeCaregiver(c.uid) }
                }) { Text(stringResource(R.string.cg_remote_unlink)) }
            },
            dismissButton = { TextButton(onClick = { unlink = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    if (askDisconnect) {
        AlertDialog(
            onDismissRequest = { askDisconnect = false },
            text = { Text(stringResource(R.string.cg_remote_disconnect_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    askDisconnect = false
                    scope.launch {
                        Remote.disconnect(context)
                        message = null
                        version++
                    }
                }) { Text(stringResource(R.string.cg_remote_disconnect), color = RojoSos) }
            },
            dismissButton = { TextButton(onClick = { askDisconnect = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    otherProject?.let { (id, text) ->
        AlertDialog(
            onDismissRequest = { otherProject = null },
            text = { Text(stringResource(R.string.cg_remote_other_project, id)) },
            confirmButton = {
                TextButton(onClick = {
                    otherProject = null
                    link(text, switch = true)
                }) { Text(stringResource(R.string.cg_remote_change)) }
            },
            dismissButton = { TextButton(onClick = { otherProject = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}
