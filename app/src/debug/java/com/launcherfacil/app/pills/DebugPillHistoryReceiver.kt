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

package com.launcherfacil.app.pills

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.launcherfacil.app.remote.Remote
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Solo en debug: historial de pastillas de ejemplo para probar y hacer capturas.
 *   adb shell am broadcast -a com.launcherfacil.app.DEBUG_PILL_HISTORY -p com.launcherfacil.app [--ei days 10]
 * Con `--ei fire <minutos>` hace sonar de verdad la toma de esa hora (como la alarma).
 */
class DebugPillHistoryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fire = intent.getIntExtra("fire", -1)
        if (fire >= 0) {
            PillAlarms.fire(context, fire, 0)
            return
        }
        val days = intent.getIntExtra("days", 10)
        val times = PillStore(context).times.ifEmpty { listOf(9 * 60, 14 * 60, 21 * 60) }
        val array = JSONArray()
        val today = LocalDate.now()
        for (d in days downTo 1) {
            val date = today.minusDays(d.toLong())
            times.forEachIndexed { i, minutes ->
                // Alguna sin confirmar (sobre todo la de la noche) y el resto con unos minutos de retraso.
                val missed = (d + i) % 7 == 0 || (i == times.lastIndex && d % 4 == 1)
                val o = JSONObject().put("d", date.toString()).put("m", minutes)
                val skipped = !missed && d % 5 == 3
                when {
                    missed -> o.put("s", "MISSED")
                    skipped -> o.put("s", "SKIPPED").put("at", minutes + 2).put("n", 1).put("of", 1)
                    else -> o.put("s", "TAKEN").put("at", minutes + (d * 3 + i * 5) % 25)
                }
                array.put(o)
            }
        }
        context.getSharedPreferences("pills", Context.MODE_PRIVATE).edit { putString("history", array.toString()) }
        Remote.publishSoon(context)
        Log.i("DebugPills", "Historial de ejemplo: $days días, ${array.length()} tomas")
    }
}
