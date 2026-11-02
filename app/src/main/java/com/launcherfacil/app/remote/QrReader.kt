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

package com.launcherfacil.app.remote

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.launcherfacil.app.gohome.decodeScaled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lee el código QR de una foto (hecha con la cámara o elegida de la galería). Así no hace
 * falta un escáner en directo ni el permiso de cámara: la foto la hace la app Cámara.
 * Se prueba a varios tamaños porque una foto de una pantalla puede salir grande y movida.
 */
suspend fun readQr(context: Context, uri: Uri): String? = withContext(Dispatchers.Default) {
    for (side in intArrayOf(1600, 1000, 2400)) {
        val bitmap = runCatching { decodeScaled(context, uri, side) }.getOrNull() ?: continue
        decodeQr(bitmap)?.let { return@withContext it }
    }
    null
}

private fun decodeQr(bitmap: Bitmap): String? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true
    )
    val reader = MultiFormatReader().apply { setHints(hints) }
    return listOf(source, source.invert()).firstNotNullOfOrNull { s ->
        try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(s))).text
        } catch (e: Exception) {
            Log.d("QrReader", "Sin QR a ${bitmap.width}px")
            null
        } finally {
            reader.reset()
        }
    }
}
