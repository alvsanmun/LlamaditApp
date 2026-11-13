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

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Las fotos van a la carpeta Pictures/LlamaditApp: las ve también la Galería del móvil,
 * el cuidador puede pasarlas al ordenador y sobreviven si se desinstala la app.
 */
private const val ALBUM = "LlamaditApp"

/** Dónde guarda la cámara la foto siguiente. */
fun newPhotoOptions(context: Context): ImageCapture.OutputFileOptions {
    val name = "LlamaditApp_${System.currentTimeMillis()}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        } else {
            val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), ALBUM)
            folder.mkdirs()
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, File(folder, name).path)
        }
    }
    return ImageCapture.OutputFileOptions.Builder(
        context.contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
    ).build()
}

/** Las fotos hechas con LlamaditApp, primero las más nuevas. */
suspend fun loadPhotos(context: Context): List<Uri> = withContext(Dispatchers.IO) {
    val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val photos = mutableListOf<Uri>()
    context.contentResolver.query(
        collection,
        arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?",
        arrayOf(ALBUM),
        "${MediaStore.Images.Media.DATE_ADDED} DESC, ${MediaStore.Images.Media._ID} DESC"
    )?.use { cursor ->
        while (cursor.moveToNext()) photos += ContentUris.withAppendedId(collection, cursor.getLong(0))
    }
    photos
}

/** Borra la foto del móvil. Devuelve false si Android no lo permite. */
suspend fun deletePhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
    try {
        context.contentResolver.delete(uri, null, null) > 0
    } catch (e: SecurityException) {
        Log.w("Photos", "No se pudo borrar la foto", e)
        false
    }
}
