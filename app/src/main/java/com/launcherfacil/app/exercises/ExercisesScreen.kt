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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.launcherfacil.app.R
import com.launcherfacil.app.ui.BigTile
import com.launcherfacil.app.ui.CaregiverMessage
import com.launcherfacil.app.ui.Fondo
import com.launcherfacil.app.ui.HomeBar
import com.launcherfacil.app.volume.VolumeController

/**
 * Botón "Ejercicios": menú con los ejercicios que ha activado el cuidador, cada uno
 * con su dibujo y las estrellas de su nivel. Si solo hay uno, se abre directamente.
 * Atrás (o la flecha) vuelve al menú; la casa, a la pantalla principal.
 */
@Composable
fun ExercisesScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val progress = remember { ExerciseProgress(context) }
    val enabled = remember { progress.enabled() }
    var selected by rememberSaveable { mutableStateOf(enabled.singleOrNull()) }

    // La voz de "¿Quién es?" va por multimedia, que la app deja a cero: se sube mientras
    // se hacen ejercicios (también al volver de apagar la pantalla) y se baja al salir.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        VolumeController.raiseMediaVolume(context, 0.6f)
        ExerciseVoice.start(context)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) VolumeController.raiseMediaVolume(context, 0.6f)
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            ExerciseVoice.stop()
            clearFaceCache()
            VolumeController.applySoundPolicy(context)
        }
    }

    val current = selected
    when {
        enabled.isEmpty() -> Column(Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp)) {
            HomeBar(onHome)
            CaregiverMessage(stringResource(R.string.exercises_none))
        }
        current != null -> {
            BackHandler(enabled = enabled.size > 1) { selected = null }
            ExerciseHost(current, progress, onHome) { level, onDone ->
                when (current) {
                    Exercise.MATH -> MathExercise(level, onDone)
                    Exercise.PAIRS -> PairsExercise(level, onDone)
                    Exercise.PAINT -> PaintExercise(level, onDone)
                    Exercise.WORDS -> WordsExercise(level, onDone)
                    Exercise.FACES -> FacesExercise(level, onDone)
                    Exercise.SEQUENCE -> SequenceExercise(level, onDone)
                    Exercise.MONEY -> MoneyExercise(level, onDone)
                    Exercise.PUZZLE -> PuzzleExercise(level, onDone)
                }
            }
        }
        else -> Column(
            Modifier.fillMaxSize().background(Fondo).safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HomeBar(onHome)
            enabled.chunked(2).forEach { row ->
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { exercise ->
                        BigTile(exercise.color, { selected = exercise }, Modifier.weight(1f).fillMaxHeight()) {
                            Column(Modifier.fillMaxSize().padding(8.dp)) {
                                FitText(exercise.emoji, Modifier.weight(1f).fillMaxWidth(), fraction = 0.7f)
                                LevelStars(progress.level(exercise), size = 20.sp)
                            }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // Con pocos ejercicios, los botones no se estiran hasta abajo.
            val rows = (enabled.size + 1) / 2
            if (rows < 3) Box(Modifier.weight((3 - rows).toFloat()))
        }
    }
}
