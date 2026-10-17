package com.launcherfacil.app.contacts

import android.content.BroadcastReceiver
import android.content.ContentProviderOperation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.Normalizer

/**
 * SOLO EN DEBUG: dibuja una cara sencilla y la guarda como foto de cada contacto
 * favorito, para ver en el emulador cómo quedan las pantallas con foto.
 *
 *   adb shell pm grant com.launcherfacil.app android.permission.WRITE_CONTACTS
 *   adb shell am broadcast -a com.launcherfacil.app.DEBUG_TEST_PHOTOS -p com.launcherfacil.app
 *
 * Con fotos reales (capturas para la documentación): si existe
 * /sdcard/Android/data/com.launcherfacil.app/files/fotos/<nombre>.jpg (nombre en
 * minúsculas y sin tildes, p. ej. "maria.jpg"), se usa esa foto en lugar del dibujo.
 */
class DebugTestPhotosReceiver : BroadcastReceiver() {

    // Fondo, pelo y piel de cada cara (una distinta por contacto).
    private val palettes = listOf(
        Triple(0xFF90CAF9, 0xFF4E342E, 0xFFF1C27D),
        Triple(0xFFF48FB1, 0xFF212121, 0xFFE0AC69),
        Triple(0xFFA5D6A7, 0xFFFFD54F, 0xFFFFDBAC),
        Triple(0xFFFFCC80, 0xFF8D6E63, 0xFFC68642),
    )

    override fun onReceive(context: Context, intent: Intent) {
        val resolver = context.contentResolver
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.DISPLAY_NAME_PRIMARY),
            "${ContactsContract.RawContacts.STARRED} = 1 AND ${ContactsContract.RawContacts.DELETED} = 0",
            null, null
        )?.use { c ->
            var i = 0
            while (c.moveToNext()) {
                val rawId = c.getLong(0)
                val photo = loadPhoto(context, c.getString(1).orEmpty())
                    ?: drawFace(palettes[i % palettes.size])
                i++
                val ops = arrayListOf(
                    ContentProviderOperation.newDelete(ContactsContract.Data.CONTENT_URI)
                        .withSelection(
                            "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                            arrayOf(rawId.toString(), Photo.CONTENT_ITEM_TYPE)
                        ).build(),
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        .withValue(ContactsContract.Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        .withValue(Photo.PHOTO, photo)
                        .build()
                )
                resolver.applyBatch(ContactsContract.AUTHORITY, ops)
                Log.i("DebugTestPhotos", "Foto de prueba guardada en el contacto $rawId")
            }
        }
    }

    /** Foto "<nombre>.jpg" de la carpeta fotos/ de la app, reducida a 720 px, o null. */
    private fun loadPhoto(context: Context, name: String): ByteArray? {
        val key = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val file = File(context.getExternalFilesDir("fotos"), "$key.jpg")
        if (!file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.path) ?: return null
        val scaled = Bitmap.createScaledBitmap(bitmap, 720, 720 * bitmap.height / bitmap.width, true)
        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    private fun drawFace(palette: Triple<Long, Long, Long>): ByteArray {
        val (bg, hair, skin) = palette
        val size = 720f
        val bitmap = Bitmap.createBitmap(size.toInt(), size.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawColor(bg.toInt())
        // Hombros, pelo, cara, ojos y sonrisa.
        paint.color = Color.rgb(69, 90, 100)
        canvas.drawOval(RectF(size * 0.15f, size * 0.78f, size * 0.85f, size * 1.3f), paint)
        paint.color = hair.toInt()
        canvas.drawOval(RectF(size * 0.25f, size * 0.12f, size * 0.75f, size * 0.66f), paint)
        paint.color = skin.toInt()
        canvas.drawOval(RectF(size * 0.30f, size * 0.22f, size * 0.70f, size * 0.74f), paint)
        paint.color = Color.rgb(33, 33, 33)
        canvas.drawCircle(size * 0.42f, size * 0.44f, size * 0.03f, paint)
        canvas.drawCircle(size * 0.58f, size * 0.44f, size * 0.03f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.018f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawArc(RectF(size * 0.40f, size * 0.50f, size * 0.60f, size * 0.64f), 20f, 140f, false, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
