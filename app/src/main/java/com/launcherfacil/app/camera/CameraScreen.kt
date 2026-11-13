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

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.launcherfacil.app.R
import com.launcherfacil.app.gohome.decodeScaled
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.HomeBar
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.TileIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "Camera"

/**
 * Cámara con solo tres cosas: lo que se ve, un botón blanco grande para hacer la foto y
 * otro para cambiar entre la cámara de atrás y la de delante. A la izquierda aparece la
 * última foto hecha, para que se note que se ha guardado.
 */
@Composable
fun CameraScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    fun allowed() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var hasPermission by remember { mutableStateOf(allowed()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permission.launch(Manifest.permission.CAMERA) }

    var front by rememberSaveable { mutableStateOf(false) }
    var canSwitch by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var lastPhoto by remember { mutableStateOf<Bitmap?>(null) }

    val preview = remember {
        // TextureView: es la que respeta las esquinas redondeadas.
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }
    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }

    // Enciende la cámara elegida; al salir de la pantalla se apaga.
    DisposableEffect(hasPermission, front, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        if (hasPermission) {
            future.addListener({
                try {
                    val provider = future.get()
                    canSwitch = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) &&
                        provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    val selector = when {
                        front && provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                        provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) -> CameraSelector.DEFAULT_BACK_CAMERA
                        else -> CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, capture)
                } catch (e: Exception) {
                    Log.e(TAG, "No se pudo abrir la cámara", e)
                    failed = true
                }
            }, ContextCompat.getMainExecutor(context))
        }
        onDispose { future.addListener({ future.get().unbindAll() }, ContextCompat.getMainExecutor(context)) }
    }

    fun takePhoto() {
        if (saving) return
        saving = true
        flash = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        capture.takePicture(
            newPhotoOptions(context),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    saving = false
                    val uri = results.savedUri ?: return
                    scope.launch {
                        lastPhoto = withContext(Dispatchers.IO) {
                            try {
                                decodeScaled(context, uri, 400)
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }
                }

                override fun onError(e: ImageCaptureException) {
                    Log.e(TAG, "No se pudo guardar la foto", e)
                    saving = false
                    failed = true
                }
            }
        )
    }

    LaunchedEffect(flash) {
        if (flash) {
            delay(150)
            flash = false
        }
    }
    LaunchedEffect(failed) {
        if (failed) {
            delay(1500)
            failed = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeBar(onHome)
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasPermission) AndroidView({ preview }, Modifier.fillMaxSize())
            if (flash) Box(Modifier.fillMaxSize().background(Color.White))
            if (failed) {
                Icon(painterResource(R.drawable.ic_close), null, tint = RojoSos, modifier = Modifier.size(160.dp))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(136.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                lastPhoto?.let {
                    Image(
                        it.asImageBitmap(), null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(16.dp))
                    )
                }
            }
            // El disparador: un círculo blanco, como en todas las cámaras.
            Box(
                Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .border(6.dp, Color.White, CircleShape)
                    .clickable(onClick = ::takePhoto)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .background(if (saving) GrisBoton else Color.White)
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (canSwitch) {
                    BigTile(GrisBoton, { front = !front }, Modifier.size(96.dp)) { TileIcon(R.drawable.ic_flip_camera, 0.7f) }
                } else {
                    Spacer(Modifier.size(96.dp))
                }
            }
        }
    }
}
