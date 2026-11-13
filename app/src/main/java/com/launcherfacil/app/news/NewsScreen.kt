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

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.exercises.ExerciseVoice
import com.launcherfacil.app.ui.AnilRadio
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.GrisBoton
import com.launcherfacil.app.ui.HomeBar
import com.launcherfacil.app.ui.RojoSos
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.TileIcon
import com.launcherfacil.app.volume.VolumeController

private val Tarjeta = Color(0xFF1F2A33)

/**
 * Periódicos: arriba, el periódico con flechas para pasar al anterior o al siguiente;
 * debajo, sus noticias con foto, bajando con el dedo. Al tocar una se ve en grande, se
 * puede escuchar y abrir entera; la flecha grande de arriba vuelve siempre al paso anterior.
 */
@Composable
fun NewsScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val papers = remember { CaregiverSettings(context).newsPapers.mapNotNull { id -> PAPERS.firstOrNull { it.id == id } } }
    // Con varias secciones (deportes, tecnología…), primero se elige la sección por su dibujo.
    val categories = remember(papers) { papers.map { it.category }.distinct() }
    var category by rememberSaveable { mutableStateOf(categories.singleOrNull()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var article by remember { mutableStateOf<Article?>(null) }
    var fullPage by remember { mutableStateOf(false) }
    // La posición de la lista de cada periódico se guarda: al volver de una noticia sigue ahí.
    val lists = remember { mutableStateMapOf<String, LazyListState>() }

    // La voz lee con el volumen de multimedia, que en la pantalla principal está a cero.
    DisposableEffect(Unit) {
        ExerciseVoice.start(context)
        VolumeController.mediaScreenOpened()
        onDispose {
            ExerciseVoice.stop()
            VolumeController.mediaScreenClosed()
            VolumeController.applySoundPolicy(context)
        }
    }

    if (papers.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp)) { HomeBar(onHome) }
        return
    }
    val current = category
    if (current == null) {
        CategoryGrid(categories, onHome) {
            category = it
            index = 0
        }
        return
    }
    val inCategory = papers.filter { it.category == current }
    val paper = inCategory[index.coerceIn(0, inCategory.lastIndex)]

    val open = article
    if (open != null && fullPage) {
        BackHandler { fullPage = false }
        FullArticle(open.link, onBack = { fullPage = false })
        return
    }
    if (open != null) {
        BackHandler {
            ExerciseVoice.speak("")
            article = null
        }
        ArticleView(open, onBack = { ExerciseVoice.speak(""); article = null }, onOpenFull = { fullPage = true })
        return
    }

    // Atrás (o la flecha de abajo) vuelve a las secciones.
    if (categories.size > 1) BackHandler { category = null }
    Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp)) {
        HomeBar(onHome)
        Spacer(Modifier.height(12.dp))
        PaperSwitcher(
            paper = paper,
            hasPrevious = index > 0,
            hasNext = index < inCategory.lastIndex,
            onPrevious = { index-- },
            onNext = { index++ }
        )
        Spacer(Modifier.height(12.dp))
        var reload by remember(paper.id) { mutableIntStateOf(0) }
        val state by produceState<Result<List<Article>>?>(null, paper.id, reload) {
            value = null
            value = runCatching { News.articles(paper, refresh = reload > 0) }
        }
        val listState = lists.getOrPut(paper.id) { LazyListState() }
        // Se lee una vez: al cambiar de periódico el estado vuelve a null mientras carga, y la
        // lista anterior no puede leerlo después (se caía la app).
        val result = state
        val articles = result?.getOrNull()
        when {
            result == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TextoClaro, strokeWidth = 8.dp, modifier = Modifier.size(96.dp))
            }
            articles.isNullOrEmpty() -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
            ) {
                TileIcon(R.drawable.ic_close, 0.3f)
                BigTile(GrisBoton, { reload++ }, Modifier.size(160.dp)) { TileIcon(R.drawable.ic_refresh, 0.6f) }
            }
            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                itemsIndexed(articles, key = { i, a -> "$i ${a.link}" }) { _, a ->
                    ArticleCard(a) { article = a }
                }
            }
        }
    }
}

/** Las secciones: botones grandes de su color con un dibujo (emoji), de dos en dos. */
@Composable
private fun CategoryGrid(categories: List<NewsCategory>, onHome: () -> Unit, onPick: (NewsCategory) -> Unit) {
    Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp)) {
        HomeBar(onHome)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(categories) { c ->
                BigTile(Color(c.color), { onPick(c) }, Modifier.fillMaxWidth().aspectRatio(1.15f)) {
                    Text(c.emoji, fontSize = 72.sp)
                }
            }
        }
    }
}

@Composable
private fun PaperSwitcher(paper: Paper, hasPrevious: Boolean, hasNext: Boolean, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(112.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Arrow(hasPrevious, reversed = false, onPrevious)
        PaperLogo(paper, Modifier.weight(1f).fillMaxHeight())
        Arrow(hasNext, reversed = true, onNext)
    }
}

@Composable
private fun Arrow(enabled: Boolean, reversed: Boolean, onClick: () -> Unit) {
    if (!enabled) {
        Spacer(Modifier.size(96.dp, 112.dp))
        return
    }
    BigTile(GrisBoton, onClick, Modifier.size(96.dp, 112.dp)) {
        Box(Modifier.rotate(if (reversed) 180f else 0f)) { TileIcon(R.drawable.ic_arrow_back, 0.6f) }
    }
}

/** El icono del periódico sobre blanco; mientras no está (o sin internet), su nombre. */
@Composable
private fun PaperLogo(paper: Paper, modifier: Modifier) {
    val context = LocalContext.current
    val logo by produceState<Bitmap?>(null, paper.id) { value = News.logo(context, paper) }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color.White).padding(10.dp), contentAlignment = Alignment.Center) {
        val bitmap = logo
        if (bitmap != null) {
            Image(bitmap.asImageBitmap(), paper.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                paper.name, color = Color(paper.color), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, lineHeight = 28.sp
            )
        }
    }
}

@Composable
private fun ArticleCard(article: Article, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Tarjeta).clickable(onClick = onClick)) {
        article.image?.let { NewsImage(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) }
        Text(
            article.title, color = TextoClaro, fontSize = 23.sp, fontWeight = FontWeight.Bold, lineHeight = 29.sp,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun NewsImage(url: String, modifier: Modifier) {
    val bitmap by produceState<Bitmap?>(null, url) { value = News.image(url) }
    Box(modifier.background(Color(0xFF2B3740))) {
        bitmap?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** Flecha grande arriba: vuelve al periódico (o a la noticia). */
@Composable
private fun BackTile(onBack: () -> Unit) {
    BigTile(GrisBoton, onBack, Modifier.fillMaxWidth().height(88.dp)) { TileIcon(R.drawable.ic_arrow_back, 0.55f) }
}

@Composable
private fun ArticleView(article: Article, onBack: () -> Unit, onOpenFull: () -> Unit) {
    val context = LocalContext.current
    var speaking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackTile(onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            article.image?.let { NewsImage(it, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(20.dp))) }
            Text(article.title, color = TextoClaro, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            if (article.summary.isNotBlank()) {
                Text(article.summary, color = TextoClaro, fontSize = 22.sp, lineHeight = 31.sp)
            }
        }
        Row(Modifier.fillMaxWidth().height(116.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Escuchar: la voz del móvil lee el titular y el resumen. Otro toque la para.
            BigTile(if (speaking) RojoSos else AnilRadio, {
                speaking = !speaking
                if (speaking) {
                    VolumeController.raiseMediaVolume(context, onlyIfSilent = true)
                    ExerciseVoice.speak(article.title + ".\n" + article.summary)
                } else {
                    ExerciseVoice.speak("")
                }
            }, Modifier.weight(1f).fillMaxHeight()) {
                TileIcon(if (speaking) R.drawable.ic_stop else R.drawable.ic_volume_up, 0.6f)
            }
            BigTile(GrisBoton, onOpenFull, Modifier.weight(1f).fillMaxHeight()) { TileIcon(R.drawable.ic_article, 0.6f) }
        }
    }
}

/** La noticia entera, en la web del periódico, sin salir de LlamaditApp (y con letra grande). */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FullArticle(url: String, onBack: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val back = {
        val view = web
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackTile(back)
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.textZoom = 140
                    settings.mediaPlaybackRequiresUserGesture = true
                    webViewClient = object : WebViewClient() {
                        // Solo páginas web: nada de abrir otras apps desde el quiosco.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            request.url.scheme !in setOf("http", "https")

                        // Sin avisos de cookies ni anuncios (ver WebCleaner).
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                            WebCleaner.intercept(request)

                        override fun onPageCommitVisible(view: WebView, url: String?) {
                            view.evaluateJavascript(WebCleaner.SCRIPT, null)
                        }

                        override fun onPageFinished(view: WebView, url: String?) {
                            view.evaluateJavascript(WebCleaner.SCRIPT, null)
                        }
                    }
                    loadUrl(url)
                    web = this
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
        )
    }
    DisposableEffect(Unit) { onDispose { web?.destroy() } }
}
