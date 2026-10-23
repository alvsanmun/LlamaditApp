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

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit
import com.launcherfacil.app.R

/** Niveles de cada ejercicio: 1 (muy fácil) a 5. */
const val MAX_LEVEL = 5

/** Aciertos seguidos para subir de nivel y fallos seguidos para bajar. */
private const val STREAK_UP = 5
private const val STREAK_DOWN = 3

/**
 * Ejercicios para mantener la memoria, la atención y el cálculo. Cada uno tiene un
 * dibujo (emoji) y un color para reconocerlo sin leer; el nombre es para el cuidador.
 */
enum class Exercise(val emoji: String, val color: Color, @StringRes val label: Int) {
    MATH("🔢", Color(0xFF1565C0), R.string.exercise_math),
    PAIRS("🃏", Color(0xFF6A1B9A), R.string.exercise_pairs),
    PAINT("🎨", Color(0xFFEF6C00), R.string.exercise_paint),
    WORDS("🔤", Color(0xFF00838F), R.string.exercise_words),
    FACES("👪", Color(0xFF2E7D32), R.string.exercise_faces),
    SEQUENCE("🎵", Color(0xFFC62828), R.string.exercise_sequence),
    MONEY("💶", Color(0xFF558B2F), R.string.exercise_money),
    PUZZLE("🧩", Color(0xFF5D4037), R.string.exercise_puzzle),
}

/**
 * Qué ejercicios están activos, el nivel de cada uno y cuántas partidas lleva
 * (SharedPreferences "exercises"). El nivel sube solo tras [STREAK_UP] partidas
 * seguidas bien hechas y baja tras [STREAK_DOWN] con fallos; el cuidador elige el
 * nivel de partida y puede cambiarlo cuando quiera.
 */
class ExerciseProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("exercises", Context.MODE_PRIVATE)

    fun isEnabled(exercise: Exercise): Boolean = prefs.getBoolean("${exercise.name}_on", true)

    fun setEnabled(exercise: Exercise, enabled: Boolean) =
        prefs.edit { putBoolean("${exercise.name}_on", enabled) }

    fun enabled(): List<Exercise> = Exercise.entries.filter(::isEnabled)

    fun level(exercise: Exercise): Int = prefs.getInt("${exercise.name}_level", 1).coerceIn(1, MAX_LEVEL)

    /** Lo usa el cuidador: empieza de nuevo la racha. */
    fun setLevel(exercise: Exercise, level: Int) = prefs.edit {
        putInt("${exercise.name}_level", level.coerceIn(1, MAX_LEVEL))
        putInt("${exercise.name}_streak", 0)
    }

    fun played(exercise: Exercise): Int = prefs.getInt("${exercise.name}_played", 0)

    fun wellDone(exercise: Exercise): Int = prefs.getInt("${exercise.name}_well", 0)

    /**
     * Apunta una partida terminada. [wellDone] = sin fallos (o casi). Devuelve el
     * nivel para la siguiente partida.
     */
    fun record(exercise: Exercise, wellDone: Boolean): Int {
        val n = exercise.name
        var level = level(exercise)
        var streak = prefs.getInt("${n}_streak", 0)
        streak = if (wellDone) maxOf(streak, 0) + 1 else minOf(streak, 0) - 1
        if (streak >= STREAK_UP) {
            if (level < MAX_LEVEL) level++
            streak = 0
        } else if (streak <= -STREAK_DOWN) {
            if (level > 1) level--
            streak = 0
        }
        prefs.edit {
            putInt("${n}_level", level)
            putInt("${n}_streak", streak)
            putInt("${n}_played", played(exercise) + 1)
            if (wellDone) putInt("${n}_well", wellDone(exercise) + 1)
        }
        return level
    }
}
