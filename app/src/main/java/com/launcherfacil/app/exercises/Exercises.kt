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
import androidx.annotation.DrawableRes
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
 * dibujo y un color para reconocerlo sin leer (diseño "Dibujos 3 · La mano hace la
 * acción"): el color de la tarjeta y uno más oscuro para el relieve. El icono se usa en
 * el panel; en el menú de juegos va una escena dibujada ([GameArt]). El nombre es para
 * el cuidador.
 */
enum class Exercise(@DrawableRes val icon: Int, val color: Color, val dark: Color, @StringRes val label: Int) {
    MATH(R.drawable.ic_game_math, Color(0xFF1565C0), Color(0xFF0D3C7A), R.string.exercise_math),
    PAIRS(R.drawable.ic_game_pairs, Color(0xFF7B3FA0), Color(0xFF4E2468), R.string.exercise_pairs),
    PAINT(R.drawable.ic_game_paint, Color(0xFFE65100), Color(0xFF9A3600), R.string.exercise_paint),
    WORDS(R.drawable.ic_game_words, Color(0xFF00796B), Color(0xFF004D43), R.string.exercise_words),
    FACES(R.drawable.ic_game_faces, Color(0xFF2E7D32), Color(0xFF1B4D1F), R.string.exercise_faces),
    SEQUENCE(R.drawable.ic_game_sequence, Color(0xFFC62828), Color(0xFF8A1717), R.string.exercise_sequence),
    MONEY(R.drawable.ic_game_money, Color(0xFF8D6E00), Color(0xFF5C4800), R.string.exercise_money),
    PUZZLE(R.drawable.ic_game_puzzle, Color(0xFF5D4037), Color(0xFF3E2723), R.string.exercise_puzzle),
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
