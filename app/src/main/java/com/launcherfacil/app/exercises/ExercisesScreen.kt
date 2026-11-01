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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.launcherfacil.app.ui.LightStatusBar
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
 * Atrás (o la flecha) y la casa vuelven a la pantalla principal: antes Atrás volvía
 * al menú y había que pulsar la flecha dos veces, y parecía que no funcionaba.
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
            // Con un solo ejercicio no hay menú: "volver a los juegos" vuelve al inicio.
            val onMenu = if (enabled.size > 1) ({ selected = null }) else onHome
            ExerciseHost(current, progress, onHome, onMenu) { level, onDone ->
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
        else -> GamesMenu(enabled, progress, onHome) { selected = it }
    }
}

/**
 * Menú de juegos (diseño "Juegos · Menú B"): arriba el botón marrón para volver y una
 * cuadrícula de dos columnas con una tarjeta por juego ([GameCard]).
 */
@Composable
private fun GamesMenu(enabled: List<Exercise>, progress: ExerciseProgress, onHome: () -> Unit, onOpen: (Exercise) -> Unit) {
    LightStatusBar()
    Column(
        Modifier.fillMaxSize().background(GmBg).safeDrawingPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        GameNavButton(onHome, Modifier.fillMaxWidth().height(72.dp))
        val rows = enabled.chunked(2)
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { exercise ->
                    GameCard(exercise, progress.level(exercise), Modifier.weight(1f).fillMaxHeight()) {
                        onOpen(exercise)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        // Con pocos juegos, las tarjetas no se estiran hasta abajo.
        if (rows.size < 4) Spacer(Modifier.weight((4 - rows.size).toFloat()))
    }
}

/**
 * Tarjeta de un juego (diseño "Dibujos 3 · La mano hace la acción"): toda de su color,
 * con una escena en la que una mano hace lo que se hace en el juego y, abajo, una franja
 * oscura con cinco barras del nivel (blancas las alcanzadas).
 */
@Composable
private fun GameCard(exercise: Exercise, level: Int, modifier: Modifier, onClick: () -> Unit) {
    RaisedBox(exercise.color, exercise.dark, modifier, radius = 24.dp, depth = 5.dp, onClick = onClick) {
        Column(Modifier.fillMaxSize()) {
            GameArt(exercise, Modifier.weight(1f).fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp))
            Row(
                Modifier.fillMaxWidth().height(26.dp).background(Color(0x2E000000)),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(MAX_LEVEL) { i ->
                    Box(
                        Modifier.size(width = 20.dp, height = 8.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (i < level) Color.White else Color.White.copy(alpha = 0.35f))
                    )
                }
            }
        }
    }
}
