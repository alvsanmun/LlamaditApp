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

package com.launcherfacil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.exercises.Exercise
import com.launcherfacil.app.exercises.ExerciseProgress
import com.launcherfacil.app.exercises.MAX_LEVEL

/**
 * Ejercicios en el panel: cada uno con su interruptor, el nivel (el actual, que sube y
 * baja solo; tocar otro número lo cambia) y cuántas partidas lleva.
 */
@Composable
internal fun ExercisesSection() {
    val context = LocalContext.current
    val progress = remember { ExerciseProgress(context) }
    // Cualquier cambio vuelve a leer lo guardado.
    var version by remember { mutableIntStateOf(0) }
    Exercise.entries.forEach { exercise ->
        val enabled = remember(version) { progress.isEnabled(exercise) }
        val level = remember(version) { progress.level(exercise) }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable {
                    progress.setEnabled(exercise, !enabled)
                    version++
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(exercise.color),
                    contentAlignment = Alignment.Center
                ) {
                    Text(exercise.emoji, fontSize = 26.sp)
                }
                Text(
                    stringResource(exercise.label),
                    color = TextoClaro,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                Switch(checked = enabled, onCheckedChange = null)
            }
            if (enabled) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.settings_exercises_level), color = TextoSuave, fontSize = 15.sp)
                    (1..MAX_LEVEL).forEach { n ->
                        val selected = n == level
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (selected) Color(0xFF90CAF9) else GrisBoton)
                                .clickable {
                                    progress.setLevel(exercise, n)
                                    version++
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(n.toString(), color = if (selected) Color.Black else TextoClaro, fontSize = 17.sp)
                        }
                    }
                }
                val played = progress.played(exercise)
                if (played > 0) {
                    Text(
                        stringResource(R.string.settings_exercises_stats, played, progress.wellDone(exercise)),
                        color = TextoSuave,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
