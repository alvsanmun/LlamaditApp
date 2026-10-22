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

package com.launcherfacil.app.gohome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Foto del portal de casa: se enseña al llegar, para que la persona lo reconozca. */
private const val FILE_NAME = "portal.jpg"
private const val MAX_SIDE = 1600

private fun Context.portalFile() = File(filesDir, FILE_NAME)

/** Archivo temporal para la cámara, compartido con ella mediante el FileProvider. */
fun cameraUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera/portal.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
}

suspend fun loadPortalPhoto(context: Context): Bitmap? = withContext(Dispatchers.IO) {
    val file = context.portalFile()
    if (file.exists()) BitmapFactory.decodeFile(file.path) else null
}

fun deletePortalPhoto(context: Context) {
    context.portalFile().delete()
}

/** Guarda la foto elegida reducida (como mucho [MAX_SIDE] px) y bien girada. */
suspend fun savePortalPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        val bitmap = decodeScaled(context, uri) ?: return@withContext false
        context.portalFile().outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        true
    } catch (e: Exception) {
        Log.w("PortalPhoto", "No se pudo guardar la foto del portal", e)
        false
    } finally {
        File(context.cacheDir, "camera/portal.jpg").delete()
    }
}

private fun decodeScaled(context: Context, uri: Uri): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder ya gira la foto según cómo se hizo (EXIF).
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val side = maxOf(info.size.width, info.size.height)
            if (side > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / side
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
