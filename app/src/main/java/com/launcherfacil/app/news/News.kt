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

package com.launcherfacil.app.news

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Html
import android.util.Log
import android.util.LruCache
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Periódicos y revistas gratuitos con su RSS, por secciones (comprobados el 2026-11-13). El
 * icono es el de su web, para que se reconozcan sin leer; si no hay, se ve el nombre sobre su color.
 */
data class Paper(val id: String, val name: String, val category: NewsCategory, val feed: String, val icon: String, val color: Long)

/** Secciones: la persona las distingue por el dibujo (emoji) y el color. */
enum class NewsCategory(val emoji: String, val color: Long) {
    GENERAL("📰", 0xFF4E5B65),
    SPORTS("⚽", 0xFF2E7D32),
    ECONOMY("💶", 0xFF00695C),
    TECH("💻", 0xFF1565C0),
    SCIENCE("🔬", 0xFF5E35B1),
    HEALTH("❤️", 0xFFC62828),
    MOTOR("🚗", 0xFF37474F),
    COOKING("🍳", 0xFFEF6C00),
    CULTURE("🎬", 0xFFAD1457),
}

val PAPERS = listOf(
    Paper("elpais", "El País", NewsCategory.GENERAL, "https://feeds.elpais.com/mrss-s/pages/ep/site/elpais.com/portada", "https://elpais.com/apple-touch-icon.png", 0xFF1A1A1A),
    Paper("elmundo", "El Mundo", NewsCategory.GENERAL, "https://e00-elmundo.uecdn.es/elmundo/rss/portada.xml", "https://www.elmundo.es/apple-touch-icon.png", 0xFF1E3A5F),
    Paper("abc", "ABC", NewsCategory.GENERAL, "https://www.abc.es/rss/2.0/portada/", "https://www.abc.es/apple-touch-icon.png", 0xFF0B4A8B),
    Paper("lavanguardia", "La Vanguardia", NewsCategory.GENERAL, "https://www.lavanguardia.com/rss/home.xml", "https://www.lavanguardia.com/apple-touch-icon.png", 0xFF003366),
    Paper("20minutos", "20minutos", NewsCategory.GENERAL, "https://www.20minutos.es/rss/", "https://www.20minutos.es/apple-touch-icon.png", 0xFF0055A4),
    Paper("eldiario", "elDiario.es", NewsCategory.GENERAL, "https://www.eldiario.es/rss/", "https://www.eldiario.es/apple-touch-icon.png", 0xFF0A4D8C),
    Paper("elconfidencial", "El Confidencial", NewsCategory.GENERAL, "https://rss.elconfidencial.com/espana/", "https://www.elconfidencial.com/img/logo/logo-el-confidencial.png", 0xFF2B6CB0),
    Paper("larazon", "La Razón", NewsCategory.GENERAL, "https://www.larazon.es/?outputType=xml", "https://www.larazon.es/public/img/larazon/favicon/apple-touch-icon.png", 0xFF9E1B32),
    Paper("europapress", "Europa Press", NewsCategory.GENERAL, "https://www.europapress.es/rss/rss.aspx", "https://www.europapress.es/apple-touch-icon.png", 0xFFD52B1E),
    Paper("elespanol", "El Español", NewsCategory.GENERAL, "https://www.elespanol.com/rss/", "https://s3.elespanol.com/k/imgp/cms/elespanol/10020766.png?h=arLG7Nxp", 0xFF4E5B65),
    Paper("huffpost", "El HuffPost", NewsCategory.GENERAL, "https://www.huffingtonpost.es/feeds/index.xml", "", 0xFF4E5B65),
    Paper("okdiario", "OKDIARIO", NewsCategory.GENERAL, "https://okdiario.com/feed", "", 0xFF4E5B65),
    Paper("libertaddigital", "Libertad Digital", NewsCategory.GENERAL, "https://feeds2.feedburner.com/libertaddigital/portada", "https://s.libertaddigital.com/logos/ld/256.png", 0xFF4E5B65),
    Paper("elindependiente", "El Independiente", NewsCategory.GENERAL, "https://www.elindependiente.com/feed/", "https://www.elindependiente.com/wp-content/uploads/2019/09/logo-220x220.jpg", 0xFF4E5B65),
    Paper("infolibre", "infoLibre", NewsCategory.GENERAL, "https://www.infolibre.es/rss", "https://www.infolibre.es/apple-touch-icon.png", 0xFF4E5B65),
    Paper("lasexta", "laSexta", NewsCategory.GENERAL, "https://www.lasexta.com/rss/351410.xml", "https://www.lasexta.com/public/img/favicon-set/icon192.webp", 0xFF4E5B65),
    Paper("antena3", "Antena 3 Noticias", NewsCategory.GENERAL, "https://www.antena3.com/noticias/rss/4013050.xml", "https://www.antena3.com/public/img/a3com/favicon/apple-touch-icon-180x180.png", 0xFF4E5B65),
    Paper("newtral", "Newtral", NewsCategory.GENERAL, "https://www.newtral.es/feed/", "https://www.newtral.es/wp-content/uploads/2022/12/NEWTRAL-AVATAR-POSITIVO-192x192-1.png", 0xFF4E5B65),
    Paper("eldebate", "El Debate", NewsCategory.GENERAL, "https://www.eldebate.com/rss/home.xml", "https://www.eldebate.com/favicon.ico", 0xFF4E5B65),
    Paper("cope", "COPE", NewsCategory.GENERAL, "https://www.cope.es/api/es/news/rss.xml", "https://www.cope.es/estaticos/apple-touch-icon-192x192.png", 0xFF4E5B65),
    Paper("marca", "Marca", NewsCategory.SPORTS, "https://e00-marca.uecdn.es/rss/portada.xml", "https://www.marca.com/apple-touch-icon.png", 0xFF2E7D32),
    Paper("as", "AS", NewsCategory.SPORTS, "https://feeds.as.com/mrss-s/pages/as/site/as.com/portada", "https://as.com/apple-touch-icon.png", 0xFF2E7D32),
    Paper("mundodeportivo", "Mundo Deportivo", NewsCategory.SPORTS, "https://www.mundodeportivo.com/rss/home.xml", "https://www.mundodeportivo.com/apple-touch-icon.png", 0xFF2E7D32),
    Paper("sport", "Sport", NewsCategory.SPORTS, "https://www.sport.es/es/rss/last-news/news.xml", "https://estaticos-cds.prensaiberica.es/v2.1.776/cds-statics/SPO/favicons/android-icon-192x192.png", 0xFF2E7D32),
    Paper("relevo", "Relevo", NewsCategory.SPORTS, "https://www.relevo.com/feed/", "", 0xFF2E7D32),
    Paper("cincodias", "Cinco Días", NewsCategory.ECONOMY, "https://feeds.elpais.com/mrss-s/pages/ep/site/cincodias.elpais.com/portada", "https://elpais.com/apple-touch-icon.png", 0xFF00695C),
    Paper("elblogsalmon", "El Blog Salmón", NewsCategory.ECONOMY, "https://www.elblogsalmon.com/feedburner.xml", "https://www.elblogsalmon.com/apple-touch-icon.png", 0xFF00695C),
    Paper("xataka", "Xataka", NewsCategory.TECH, "https://www.xataka.com/feedburner.xml", "https://www.xataka.com/apple-touch-icon.png", 0xFF1565C0),
    Paper("genbeta", "Genbeta", NewsCategory.TECH, "https://www.genbeta.com/feedburner.xml", "https://www.genbeta.com/apple-touch-icon.png", 0xFF1565C0),
    Paper("xatakamovil", "Xataka Móvil", NewsCategory.TECH, "https://www.xatakamovil.com/feedburner.xml", "https://www.xatakamovil.com/apple-touch-icon.png", 0xFF1565C0),
    Paper("applesfera", "Applesfera", NewsCategory.TECH, "https://www.applesfera.com/feedburner.xml", "https://www.applesfera.com/apple-touch-icon.png", 0xFF1565C0),
    Paper("hipertextual", "Hipertextual", NewsCategory.TECH, "https://hipertextual.com/feed", "", 0xFF1565C0),
    Paper("computerhoy", "Computer Hoy", NewsCategory.TECH, "https://computerhoy.20minutos.es/rss/", "https://computerhoy.20minutos.es/apple-touch-icon.png", 0xFF1565C0),
    Paper("andro4all", "Andro4all", NewsCategory.TECH, "https://andro4all.com/feed", "https://www.larazon.es/public/img/larazon/favicon/apple-touch-icon.png", 0xFF1565C0),
    Paper("muyinteresante", "Muy Interesante", NewsCategory.SCIENCE, "https://www.muyinteresante.com/rss", "https://muyinteresante.okdiario.com/wp-content/uploads/sites/5/2024/09/cropped-muy-favicon.png?w=192", 0xFF5E35B1),
    Paper("xatakaciencia", "Xataka Ciencia", NewsCategory.SCIENCE, "https://www.xatakaciencia.com/feedburner.xml", "https://www.xatakaciencia.com/apple-touch-icon.png", 0xFF5E35B1),
    Paper("vitonica", "Vitónica", NewsCategory.HEALTH, "https://www.vitonica.com/feedburner.xml", "https://www.vitonica.com/apple-touch-icon.png", 0xFFC62828),
    Paper("saludelmundo", "Salud (El Mundo)", NewsCategory.HEALTH, "https://e00-elmundo.uecdn.es/elmundosalud/rss/portada.xml", "https://www.elmundo.es/apple-touch-icon.png", 0xFFC62828),
    Paper("motorpasion", "Motorpasión", NewsCategory.MOTOR, "https://www.motorpasion.com/feedburner.xml", "https://www.motorpasion.com/apple-touch-icon.png", 0xFF37474F),
    Paper("directoalpaladar", "Directo al Paladar", NewsCategory.COOKING, "https://www.directoalpaladar.com/feedburner.xml", "https://www.directoalpaladar.com/apple-touch-icon.png", 0xFFEF6C00),
    Paper("espinof", "Espinof", NewsCategory.CULTURE, "https://www.espinof.com/feedburner.xml", "https://www.espinof.com/apple-touch-icon.png", 0xFFAD1457),
    Paper("fotogramas", "Fotogramas", NewsCategory.CULTURE, "https://www.fotogramas.es/rss/all.xml/", "https://www.fotogramas.es/_assets/design-tokens/fotogramas/static/images/apple-touch-icon.f89a6f5.png", 0xFFAD1457),
)

data class Article(val title: String, val summary: String, val link: String, val image: String?)

object News {
    private const val TAG = "News"
    private const val FRESH_MS = 15 * 60_000L
    private const val MAX_SUMMARY = 3000

    private val cache = mutableMapOf<String, Pair<Long, List<Article>>>()
    private val images = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Las noticias del periódico (de la memoria si son de hace menos de 15 minutos). */
    suspend fun articles(paper: Paper, refresh: Boolean = false): List<Article> = withContext(Dispatchers.IO) {
        val cached = cache[paper.id]
        if (!refresh && cached != null && System.currentTimeMillis() - cached.first < FRESH_MS) return@withContext cached.second
        val list = open(paper.feed).use { parse(it) }
        cache[paper.id] = System.currentTimeMillis() to list
        list
    }

    private fun open(url: String): InputStream {
        var address = url
        repeat(5) {
            val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) LlamaditApp")
            }
            when (connection.responseCode) {
                in 300..399 -> {
                    address = URL(URL(address), connection.getHeaderField("Location")).toString()
                    connection.disconnect()
                }
                in 200..299 -> return connection.inputStream
                else -> error("HTTP ${connection.responseCode}")
            }
        }
        error("Demasiadas redirecciones")
    }

    /** RSS (item) y Atom (entry). Busca la imagen en media:content, enclosure o la primera <img>. */
    private fun parse(input: InputStream): List<Article> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        val articles = mutableListOf<Article>()
        var inItem = false
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var image: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            val name = parser.name
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when {
                    name == "item" || name == "entry" -> {
                        inItem = true
                        title = ""; link = ""; summary = ""; content = ""; image = null
                    }
                    !inItem -> Unit
                    name == "title" -> title = parser.readText()
                    name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        if (href != null) {
                            if (rel == null || rel == "alternate") link = href
                        } else {
                            link = parser.readText()
                        }
                    }
                    name == "description" || name == "summary" -> summary = parser.readText()
                    name == "content:encoded" || name == "content" -> {
                        if (parser.getAttributeValue(null, "url") != null) {
                            if (image == null) image = parser.imageUrl()
                        } else {
                            content = parser.readText()
                        }
                    }
                    name == "media:content" || name == "media:thumbnail" || name == "enclosure" ->
                        if (image == null) image = parser.imageUrl()
                }
                XmlPullParser.END_TAG -> if (inItem && (name == "item" || name == "entry")) {
                    inItem = false
                    val text = listOf(summary, content).map { cleanText(it) }.maxByOrNull { it.length }.orEmpty()
                    val picture = image ?: firstImage(content) ?: firstImage(summary)
                    if (title.isNotBlank() && link.isNotBlank()) {
                        articles += Article(cleanText(title), text.take(MAX_SUMMARY), link.trim(), picture)
                    }
                }
            }
        }
        return articles
    }

    /** Todo el texto de la etiqueta, aunque lleve otras dentro (Atom con XHTML). */
    private fun XmlPullParser.readText(): String {
        val text = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.TEXT -> text.append(this.text)
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        return text.toString()
    }

    private fun XmlPullParser.imageUrl(): String? {
        val url = getAttributeValue(null, "url") ?: return null
        val type = getAttributeValue(null, "type")
        val medium = getAttributeValue(null, "medium")
        return url.takeIf { (type == null || type.startsWith("image")) && (medium == null || medium == "image") }
    }

    private fun firstImage(html: String): String? =
        Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)

    private fun cleanText(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT).toString()
            .replace('￼', ' ')
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\s*\\n\\s*"), "\n\n")
            .trim()

    /** Foto de una noticia, reducida al ancho de la pantalla. */
    suspend fun image(url: String, maxWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        images.get(url)?.let { return@withContext it }
        try {
            val bytes = open(url).use { it.readBytes() }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.also { images.put(url, it) }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo bajar la imagen $url", e)
            null
        }
    }

    /** Icono del periódico: se baja una vez y se guarda en el móvil. */
    suspend fun logo(context: Context, paper: Paper): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "news/${paper.id}.png")
        if (!file.exists() && paper.icon.isNotEmpty()) {
            try {
                val bytes = open(paper.icon).use { it.readBytes() }
                if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) {
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo bajar el icono de ${paper.name}", e)
            }
        }
        file.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }
}
