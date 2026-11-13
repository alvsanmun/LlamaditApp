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

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import com.launcherfacil.app.alerts.ReminderActivity
import com.launcherfacil.app.remote.Remote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

/**
 * Una pastilla que hay que tomar a una hora: la foto de su caja (en `files/pills/<id>.jpg`)
 * y cuántas, en medias (0,5 = media pastilla). Las de la misma hora forman una toma.
 */
data class PillDose(val id: String, val minutes: Int, val count: Float)

/** Las cantidades que se pueden elegir. */
val PILL_COUNTS = listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 4f, 5f)

/** "1", "½", "1½"… */
fun formatPillCount(count: Float): String {
    val whole = count.toInt()
    val half = count - whole >= 0.5f
    return when {
        whole == 0 -> "½"
        half -> "$whole½"
        else -> "$whole"
    }
}

/**
 * Cómo acabó una toma: tomada, la persona dijo que no se la toma (alguna o todas las cajas),
 * sin confirmar o sonando todavía.
 */
enum class PillStatus { TAKEN, SKIPPED, MISSED, PENDING }

/**
 * Una toma del historial: el día, la hora de la toma y, si contestó, a qué hora ([at]). Si
 * dijo que no a alguna caja, [skipped] de [total].
 */
data class PillRecord(
    val date: LocalDate, val minutes: Int, val status: PillStatus, val at: Int?,
    val skipped: Int = 0, val total: Int = 0
)

fun formatPillTime(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

class PillStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("pills", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit { putBoolean("enabled", value) }

    val doses: List<PillDose>
        get() {
            val array = JSONArray(prefs.getString("doses", "[]"))
            return (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                PillDose(o.getString("id"), o.getInt("minutes"), o.getDouble("count").toFloat())
            }.sortedBy { it.minutes }
        }

    private fun save(list: List<PillDose>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("minutes", it.minutes).put("count", it.count.toDouble())) }
        prefs.edit { putString("doses", array.toString()) }
    }

    /** Las horas con alguna pastilla, de la mañana a la noche. */
    val times: List<Int> get() = doses.map { it.minutes }.distinct().sorted()

    /** Las pastillas de una hora, en el orden en que se añadieron. */
    fun dosesAt(minutes: Int): List<PillDose> {
        val array = JSONArray(prefs.getString("doses", "[]"))
        val order = (0 until array.length()).map { array.getJSONObject(it).getString("id") }
        return doses.filter { it.minutes == minutes }.sortedBy { order.indexOf(it.id) }
    }

    fun add(minutes: Int, count: Float, photo: Bitmap?): PillDose {
        val dose = PillDose(UUID.randomUUID().toString().take(12), minutes, count)
        save(doses + dose)
        photo?.let { savePhoto(dose.id, it) }
        return dose
    }

    fun update(id: String, minutes: Int, count: Float, photo: Bitmap?) {
        save(doses.map { if (it.id == id) it.copy(minutes = minutes, count = count) else it })
        photo?.let { savePhoto(id, it) }
    }

    fun remove(id: String) {
        save(doses.filter { it.id != id })
        photoFile(id).delete()
    }

    fun photoFile(id: String) = File(appContext.filesDir, "pills/$id.jpg")

    fun hasPhoto(id: String) = photoFile(id).exists()

    fun loadPhoto(id: String): Bitmap? = photoFile(id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    private fun savePhoto(id: String, bitmap: Bitmap) {
        val file = photoFile(id).apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
    }

    /** Si la toma de esa hora ya se ha confirmado hoy. */
    fun takenToday(minutes: Int): Boolean = prefs.getString("taken_$minutes", null) == LocalDate.now().toString()

    fun markTaken(minutes: Int) = prefs.edit { putString("taken_$minutes", LocalDate.now().toString()) }

    /** Ya se ha avisado al cuidador de que hoy no se ha confirmado esa toma. */
    fun missedReported(minutes: Int): Boolean = prefs.getString("missed_$minutes", null) == LocalDate.now().toString()

    fun markMissedReported(minutes: Int) = prefs.edit { putString("missed_$minutes", LocalDate.now().toString()) }

    // --- Historial: para que el cuidador sepa si se las está tomando ---

    /**
     * Apunta cómo va la toma de hoy a esa hora. Una toma confirmada no cambia, y "sonando"
     * no pisa lo que ya hubiera.
     */
    fun record(minutes: Int, status: PillStatus, skipped: Int = 0, total: Int = 0) {
        val today = LocalDate.now()
        val list = history(convertOld = false).toMutableList()
        val i = list.indexOfFirst { it.date == today && it.minutes == minutes }
        val old = list.getOrNull(i)
        // Lo que contestó la persona (tomada o no) ya no cambia.
        if (old?.status == PillStatus.TAKEN || old?.status == PillStatus.SKIPPED) return
        if (status == PillStatus.PENDING && old != null) return
        val now = LocalTime.now()
        val answered = status == PillStatus.TAKEN || status == PillStatus.SKIPPED
        val record = PillRecord(today, minutes, status, if (answered) now.hour * 60 + now.minute else null, skipped, total)
        if (i >= 0) list[i] = record else list.add(record)
        val from = today.minusDays(HISTORY_DAYS.toLong())
        val array = JSONArray()
        list.filter { it.date.isAfter(from) }.forEach { r ->
            array.put(JSONObject().put("d", r.date.toString()).put("m", r.minutes).put("s", r.status.name).apply {
                r.at?.let { put("at", it) }
                if (r.status == PillStatus.SKIPPED) put("n", r.skipped).put("of", r.total)
            })
        }
        prefs.edit { putString("history", array.toString()) }
    }

    /**
     * Las tomas apuntadas, del día más nuevo al más viejo y por horas. Una que sonó un día
     * anterior y nunca se confirmó cuenta como no confirmada.
     */
    fun history(convertOld: Boolean = true): List<PillRecord> {
        val array = JSONArray(prefs.getString("history", "[]"))
        val today = LocalDate.now()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.getJSONObject(i)
            try {
                val date = LocalDate.parse(o.getString("d"))
                var status = PillStatus.valueOf(o.getString("s"))
                if (convertOld && status == PillStatus.PENDING && date.isBefore(today)) status = PillStatus.MISSED
                PillRecord(date, o.getInt("m"), status, if (o.has("at")) o.getInt("at") else null, o.optInt("n"), o.optInt("of"))
            } catch (e: Exception) {
                null
            }
        }.sortedWith(compareByDescending<PillRecord> { it.date }.thenBy { it.minutes })
    }

    /** Tomas tomadas del todo y tomas en total de los últimos [days] días (sin las que suenan ahora). */
    fun summary(days: Int = 7): Pair<Int, Int> {
        val from = LocalDate.now().minusDays(days.toLong())
        val done = history().filter { it.date.isAfter(from) && it.status != PillStatus.PENDING }
        return done.count { it.status == PillStatus.TAKEN } to done.size
    }

    companion object {
        /** Días que se guardan en el historial. */
        const val HISTORY_DAYS = 30
    }
}

/**
 * Alarmas de las pastillas. Hay una para la próxima toma; al sonar se programa la siguiente.
 * Mientras no se confirme, el aviso vuelve cada [REPEAT_MINUTES] minutos, y a la hora se
 * apunta como no tomada para que lo vea el cuidador.
 */
object PillAlarms {
    private const val TAG = "PillAlarms"
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_ATTEMPT = "attempt"
    private const val REPEAT_MINUTES = 15L
    /** Avisos que se dan como mucho (a la hora, y otros tres cada 15 min); luego, "no tomada". */
    private const val SHOWS = 4

    private fun intent(context: Context, minutes: Int, attempt: Int, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, PillAlarmReceiver::class.java)
                .putExtra(EXTRA_MINUTES, minutes)
                .putExtra(EXTRA_ATTEMPT, attempt),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Programa la próxima toma. Se llama al cambiar las pastillas, al encender y al sonar. */
    fun schedule(context: Context) {
        val store = PillStore(context)
        val alarms = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        val next = store.times.takeIf { store.enabled }?.minOfOrNull { minutes ->
            val today = LocalDate.now().atTime(LocalTime.of(minutes / 60, minutes % 60))
            if (today.isAfter(now)) today else today.plusDays(1)
        }
        val main = intent(context, next?.let { it.hour * 60 + it.minute } ?: 0, 0, 0)
        if (next == null) {
            alarms.cancel(main)
            return
        }
        set(context, next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), main)
        Log.i(TAG, "Próxima toma: $next")
    }

    private fun set(context: Context, at: Long, pending: PendingIntent) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** Vuelve a avisar dentro de un rato si no se confirma. Cada hora tiene la suya. */
    fun scheduleRepeat(context: Context, minutes: Int, attempt: Int) {
        val at = System.currentTimeMillis() + REPEAT_MINUTES * 60_000
        set(context, at, intent(context, minutes, attempt, REPEAT_CODE + minutes))
    }

    fun cancelRepeat(context: Context, minutes: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(intent(context, minutes, 0, REPEAT_CODE + minutes))
    }

    /**
     * La persona ha contestado a todas las cajas de esa hora: con el verde (tomada) o con el
     * rojo (no se la toma, [skipped] de [total]). En los dos casos el aviso deja de sonar.
     */
    fun confirm(context: Context, minutes: Int, skipped: Int = 0, total: Int = 0) {
        val store = PillStore(context)
        store.markTaken(minutes)
        if (skipped > 0) store.record(minutes, PillStatus.SKIPPED, skipped, total)
        else store.record(minutes, PillStatus.TAKEN)
        cancelRepeat(context, minutes)
        Remote.publishSoon(context)
        if (skipped > 0) {
            Remote.addEvent(context, "pillsSkipped", mapOf("time" to formatPillTime(minutes), "skipped" to skipped, "total" to total))
        } else {
            Remote.addEvent(context, "pillsTaken", mapOf("time" to formatPillTime(minutes)))
        }
    }

    internal fun fire(context: Context, minutes: Int, attempt: Int) {
        if (attempt == 0) schedule(context)
        val store = PillStore(context)
        if (!store.enabled || store.dosesAt(minutes).isEmpty() || store.takenToday(minutes)) return
        if (attempt >= SHOWS) {
            if (!store.missedReported(minutes)) {
                store.markMissedReported(minutes)
                store.record(minutes, PillStatus.MISSED)
                Remote.publishSoon(context)
                Remote.addEvent(context, "pillsMissed", mapOf("time" to formatPillTime(minutes)))
            }
            return
        }
        if (attempt == 0) {
            store.record(minutes, PillStatus.PENDING)
            Remote.publishSoon(context)
        }
        scheduleRepeat(context, minutes, attempt + 1)
        context.startActivity(ReminderActivity.pills(context, minutes))
    }

    private const val REPEAT_CODE = 10_000
}

class PillAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PillAlarms.fire(context, intent.getIntExtra(PillAlarms.EXTRA_MINUTES, -1), intent.getIntExtra(PillAlarms.EXTRA_ATTEMPT, 0))
    }
}
