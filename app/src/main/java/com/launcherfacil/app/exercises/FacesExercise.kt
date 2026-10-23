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

package com.launcherfacil.app.exercises

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.ui.AzulTeclado
import com.launcherfacil.app.ui.BateriaAlta
import com.launcherfacil.app.ui.BateriaBaja
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.CaregiverMessage
import com.launcherfacil.app.ui.TextoClaro
import com.launcherfacil.app.ui.TileIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Fotos para elegir según el nivel (como mucho, las que haya). */
private val FACE_OPTIONS = listOf(2, 3, 4, 4, 6)

/** Fotos de los favoritos, cargadas una vez mientras se hacen ejercicios. */
private var faceCache: List<Pair<Contact, ImageBitmap>>? = null

/**
 * ¿Quién es?: la voz del móvil pregunta por un familiar ("¿Dónde está María?") y hay
 * que tocar su foto entre varias de los favoritos. El altavoz repite la pregunta.
 * Bien hecha si se acierta a la primera.
 */
@Composable
internal fun FacesExercise(level: Int, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val faces by produceState(faceCache) {
        if (value == null) {
            value = loadFavoriteContacts(context).mapNotNull { c -> loadContactPhoto(context, c.id)?.let { c to it } }
                .also { faceCache = it }
        }
    }
    val all = faces ?: return
    if (all.size < 2) {
        CaregiverMessage(stringResource(R.string.exercise_faces_none))
        return
    }
    val options = remember { all.shuffled().take(FACE_OPTIONS[level - 1].coerceAtMost(all.size)) }
    val target = remember { options.random() }
    val wrong = remember { mutableStateListOf<Int>() }
    var solved by remember { mutableStateOf(false) }
    val question = stringResource(R.string.exercise_faces_where, target.first.name)
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(Unit) { ExerciseVoice.speak(question) }

    fun tap(index: Int) {
        if (solved || index in wrong) return
        if (options[index] == target) {
            solved = true
            scope.launch {
                delay(500)
                onDone(wrong.isEmpty())
            }
        } else {
            wrong += index
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            ExerciseVoice.speak(question)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigTile(AzulTeclado, { ExerciseVoice.speak(question) }, Modifier.fillMaxWidth().height(96.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp)) { TileIcon(R.drawable.ic_volume_up, 1f) }
                Text(
                    "  " + target.first.name,
                    color = TextoClaro,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val cols = if (options.size <= 2) 1 else 2
        options.indices.chunked(cols).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { index ->
                    Face(
                        photo = options[index].second,
                        state = when {
                            index in wrong -> false
                            solved && options[index] == target -> true
                            else -> null
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    ) { tap(index) }
                }
                if (row.size < cols) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Foto que se puede tocar: verde si es la buena, apagada con una cruz si no. */
@Composable
private fun Face(photo: ImageBitmap, state: Boolean?, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .clip(shape)
            .background(Color.Black)
            .then(
                when (state) {
                    true -> Modifier.border(8.dp, BateriaAlta, shape)
                    false -> Modifier.border(4.dp, BateriaBaja, shape)
                    null -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (state == false) 0.35f else 1f)
        )
        if (state == false) {
            Icon(painterResource(R.drawable.ic_close), null, tint = BateriaBaja, modifier = Modifier.size(96.dp))
        }
    }
}

/** Al salir de los ejercicios se olvidan las fotos (por si el cuidador cambia algún favorito). */
internal fun clearFaceCache() {
    faceCache = null
}
