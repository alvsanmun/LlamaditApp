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

import android.app.Activity
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.alerts.ReminderActivity
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.HomeItem
import com.launcherfacil.app.gohome.cameraUri
import com.launcherfacil.app.gohome.decodeScaled
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.news.NewsCategory
import com.launcherfacil.app.news.PAPERS
import com.launcherfacil.app.pills.PILL_COUNTS
import com.launcherfacil.app.pills.PillAlarms
import com.launcherfacil.app.pills.PillDose
import com.launcherfacil.app.pills.PillBox
import com.launcherfacil.app.pills.PillRecord
import com.launcherfacil.app.pills.PillStatus
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.launcherfacil.app.pills.PillStore
import com.launcherfacil.app.pills.formatPillCount
import com.launcherfacil.app.pills.formatPillTime
import com.launcherfacil.app.remote.Remote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Niveles que se pueden elegir para el aviso de batería baja. */
val LOW_BATTERY_LEVELS = listOf(10, 15, 20, 25, 30, 40, 50)

/** Batería baja: activar el aviso y elegir a qué porcentaje sale. */
@Composable
internal fun BatteryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var on by remember { mutableStateOf(settings.lowBatteryAlert) }
    var level by remember { mutableIntStateOf(settings.lowBatteryPercent) }
    CgPage(stringResource(R.string.cg_battery), R.drawable.ic_chevron_left, onBack) {
        CgCard {
            SwitchRow(stringResource(R.string.battery_alert_switch), on) {
                on = it
                settings.lowBatteryAlert = it
                Remote.publishSoon(context)
            }
            CgHint(stringResource(R.string.battery_alert_hint))
        }
        if (on) {
            CgLabel(stringResource(R.string.battery_alert_level))
            CgCard {
                ChoiceRow(LOW_BATTERY_LEVELS, level, { "$it %" }) {
                    level = it
                    settings.lowBatteryPercent = it
                    Remote.publishSoon(context)
                }
            }
        }
        CgCard { ActionButton(stringResource(R.string.alert_test), { context.startActivity(ReminderActivity.battery(context)) }) }
    }
}

/** Fila de botones para elegir un valor (porcentaje, cantidad de pastillas…). */
@Composable
private fun <T> ChoiceRow(values: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    val on = value == selected
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color(0xFFE3F2FD) else CgNeutral)
                            .border(2.dp, if (on) CgAccent else Color.Transparent, RoundedCornerShape(14.dp))
                            .clickable { onPick(value) },
                        contentAlignment = Alignment.Center
                    ) { Text(label(value), color = CgInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
                }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** Pastillas: activar los avisos y la lista de tomas, por horas. */
@Composable
internal fun PillsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PillStore(context) }
    var on by remember { mutableStateOf(store.enabled) }
    var version by remember { mutableIntStateOf(0) }
    // null = lista; "" = pastilla nueva; si no, el id de la que se cambia.
    var editing by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val doses = remember(version) { store.doses }

    if (showHistory) {
        BackHandler { showHistory = false }
        PillHistoryPage(store) { showHistory = false }
        return
    }

    val current = editing
    if (current != null) {
        BackHandler { editing = null }
        PillFormPage(
            dose = doses.firstOrNull { it.id == current },
            onDone = {
                editing = null
                version++
                PillAlarms.schedule(context)
                Remote.publishSoon(context)
            }
        )
        return
    }

    CgPage(stringResource(R.string.cg_pills), R.drawable.ic_chevron_left, onBack) {
        CgCard {
            SwitchRow(stringResource(R.string.pills_switch), on) {
                on = it
                store.enabled = it
                PillAlarms.schedule(context)
                Remote.publishSoon(context)
            }
            CgHint(stringResource(R.string.pills_hint))
        }
        // Historial: si se las está tomando.
        // Se relee al volver (por ejemplo, tras el aviso de una toma).
        val history = rememberOnResume { store.history() }
        if (history.isNotEmpty()) {
            CgLabel(stringResource(R.string.pills_history))
            CgCard {
                val (taken, total) = remember(history) { store.summary() }
                Text(
                    stringResource(R.string.pills_history_summary, taken, total),
                    color = if (taken < total) CgWarn else CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                history.filter { it.date == LocalDate.now() }.forEach { PillRecordRow(it) }
                ActionButton(stringResource(R.string.pills_history_open), { showHistory = true })
            }
        }
        store.times.forEach { minutes ->
            CgLabel(formatPillTime(minutes))
            CgCard(padding = 8) {
                doses.filter { it.minutes == minutes }.forEach { dose ->
                    PillRow(store, dose, version) { editing = dose.id }
                }
                ActionButton(stringResource(R.string.alert_test), { context.startActivity(ReminderActivity.pills(context, minutes, test = true)) })
            }
        }
        CgCard { ActionButton(stringResource(R.string.pills_add), { editing = "" }, color = CgAccent) }
    }
}

/** Todo el historial (30 días), por días. */
@Composable
private fun PillHistoryPage(store: PillStore, onBack: () -> Unit) {
    val history = rememberOnResume { store.history() }
    CgPage(stringResource(R.string.pills_history), R.drawable.ic_chevron_left, onBack) {
        if (history.isEmpty()) CgHint(stringResource(R.string.pills_history_empty))
        history.groupBy { it.date }.forEach { (date, records) ->
            CgLabel(dayLabel(date))
            CgCard(padding = 8) { records.forEach { PillRecordRow(it) } }
        }
    }
}

@Composable
private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.pills_today)
        today.minusDays(1) -> stringResource(R.string.pills_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
            .replaceFirstChar { it.titlecase(Locale.getDefault()) }
    }
}

/** Una toma: la hora, un círculo verde (✓), rojo (✗) o gris (sonando) y qué pasó. */
@Composable
private fun PillRecordRow(record: PillRecord) {
    val (color, icon, text) = when (record.status) {
        PillStatus.TAKEN -> Triple(
            Color(0xFF2E7D32), R.drawable.ic_check,
            stringResource(R.string.pills_status_taken, formatPillTime(record.at ?: record.minutes))
        )
        PillStatus.SKIPPED -> Triple(
            Color(0xFFE65100), R.drawable.ic_close,
            if (record.total > 1 && record.skipped < record.total) stringResource(R.string.pills_status_skipped_some, record.skipped, record.total)
            else stringResource(R.string.pills_status_skipped)
        )
        PillStatus.MISSED -> Triple(Color(0xFFC62828), R.drawable.ic_close, stringResource(R.string.pills_status_missed))
        PillStatus.PENDING -> Triple(Color(0xFF8A96A5), R.drawable.ic_snooze, stringResource(R.string.pills_status_pending))
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatPillTime(record.minutes), color = CgInk, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(64.dp))
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(color), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text(text, color = when (record.status) {
                PillStatus.MISSED -> Color(0xFFC62828)
                PillStatus.SKIPPED -> Color(0xFFE65100)
                else -> CgInk
            }, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        // Qué pastillas eran: la foto de cada caja, cuántas y si la tomó (✓) o no (✗).
        if (record.boxes.isNotEmpty()) {
            Row(
                Modifier.padding(start = 64.dp, top = 8.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                record.boxes.forEach { PillBoxThumb(it) }
            }
        }
    }
}

@Composable
private fun PillBoxThumb(box: PillBox) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(null, box.id) { value = withContext(Dispatchers.IO) { PillStore(context).loadThumb(box.id) } }
    Box(Modifier.size(90.dp)) {
        PillThumb(photo, Modifier.size(82.dp).align(Alignment.BottomStart))
        Text(
            "× " + formatPillCount(box.count), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 3.dp, bottom = 3.dp)
                .clip(RoundedCornerShape(6.dp)).background(Color(0xCC1B2430)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
        val taken = box.taken
        if (taken != null) {
            Box(
                Modifier.size(24.dp).align(Alignment.TopEnd).clip(RoundedCornerShape(12.dp))
                    .background(if (taken) Color(0xFF2E7D32) else Color(0xFFE65100)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(if (taken) R.drawable.ic_check else R.drawable.ic_close), null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PillRow(store: PillStore, dose: PillDose, version: Int, onClick: () -> Unit) {
    val photo by produceState<Bitmap?>(null, dose.id, version) { value = withContext(Dispatchers.IO) { store.loadPhoto(dose.id) } }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PillThumb(photo, Modifier.size(64.dp))
        Text("× " + formatPillCount(dose.count), color = CgInk, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = CgMuted)
    }
}

@Composable
private fun PillThumb(photo: Bitmap?, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xFFD9DEE4)), contentAlignment = Alignment.Center) {
        if (photo != null) {
            Image(photo.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_medication), null, tint = Color(0xFF9AA4AF), modifier = Modifier.fillMaxSize(0.7f))
        }
    }
}

/** Una pastilla: hora, cuántas y la foto de su caja. */
@Composable
private fun PillFormPage(dose: PillDose?, onDone: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity
    val store = remember { PillStore(context) }
    val scope = rememberCoroutineScope()
    var minutes by remember { mutableIntStateOf(dose?.minutes ?: 9 * 60) }
    var count by remember { mutableStateOf(dose?.count ?: 1f) }
    var draft by remember { mutableStateOf<Bitmap?>(null) }
    var photoFailed by remember { mutableStateOf(false) }
    val existing by produceState<Bitmap?>(null, dose?.id) {
        value = dose?.let { withContext(Dispatchers.IO) { store.loadPhoto(it.id) } }
    }

    fun gotPhoto(uri: android.net.Uri) = scope.launch {
        val bitmap = withContext(Dispatchers.IO) { runCatching { decodeScaled(context, uri, 1080) }.getOrNull() }
        photoFailed = bitmap == null
        if (bitmap != null) draft = bitmap
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) gotPhoto(cameraUri(context, CAMERA_FILE))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) gotPhoto(uri)
    }

    CgPage(stringResource(R.string.pills_edit_title), R.drawable.ic_chevron_left, onDone) {
        CgLabel(stringResource(R.string.pills_time))
        CgCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeStepper(minutes / 60, 1, 24) { minutes = it * 60 + minutes % 60 }
                Text(":", color = CgInk, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                TimeStepper(minutes % 60, 5, 60) { minutes = minutes / 60 * 60 + it }
            }
        }
        CgLabel(stringResource(R.string.pills_count))
        CgCard { ChoiceRow(PILL_COUNTS, count, ::formatPillCount) { count = it } }
        CgLabel(stringResource(R.string.cg_form_photo))
        CgCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PillThumb(draft ?: existing, Modifier.size(112.dp))
                Box(Modifier.weight(1f)) { CgHint(stringResource(R.string.pills_photo_hint)) }
            }
            if (photoFailed) CgWarning(stringResource(R.string.settings_house_photo_failed))
            ActionButton(stringResource(R.string.cg_form_camera), {
                // La cámara y la galería son otras apps: se sale un momento del quiosco.
                Kiosk.unlock(activity)
                try {
                    camera.launch(cameraUri(context, CAMERA_FILE))
                } catch (e: ActivityNotFoundException) {
                    photoFailed = true
                }
            })
            ActionButton(stringResource(R.string.cg_form_gallery), {
                Kiosk.unlock(activity)
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
        }
        CgCard {
            ActionButton(stringResource(R.string.cg_form_save), {
                if (dose == null) store.add(minutes, count, draft) else store.update(dose.id, minutes, count, draft)
                onDone()
            }, color = VerdeLlamar)
            if (dose != null) {
                ActionButton(stringResource(R.string.pills_delete), {
                    store.remove(dose.id)
                    onDone()
                }, color = RojoSos)
            }
        }
    }
}

private const val CAMERA_FILE = "pill.jpg"

/** Un número con − y +, que da la vuelta (23 → 0). */
@Composable
private fun TimeStepper(value: Int, step: Int, limit: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StepButton("−") { onChange(((value - step) % limit + limit) % limit) }
        Text("%02d".format(value), color = CgInk, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(52.dp))
        StepButton("+") { onChange((value + step) % limit) }
    }
}

@Composable
private fun StepButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(CgNeutral).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = CgInk, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
}

/** Periódicos: el botón en la pantalla principal y qué periódicos se ven. */
@Composable
internal fun NewsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var visible by remember { mutableStateOf(settings.homeLayout.any { it.item == HomeItem.NEWS && it.visible }) }
    var chosen by remember { mutableStateOf(settings.newsPapers.toSet()) }
    CgPage(stringResource(R.string.cg_news), R.drawable.ic_chevron_left, onBack) {
        CgCard {
            SwitchRow(stringResource(R.string.news_switch_button), visible) { on ->
                visible = on
                settings.homeLayout = settings.homeLayout.map { if (it.item == HomeItem.NEWS) it.copy(visible = on) else it }
                Remote.publishSoon(context)
            }
            CgHint(stringResource(R.string.news_hint))
            CgWarning(stringResource(R.string.news_data_note))
        }
        NewsCategory.entries.forEach { category ->
            CgLabel(category.emoji + "  " + stringResource(category.label()))
            CgCard(padding = 8) {
            PAPERS.filter { it.category == category }.forEach { paper ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                        chosen = if (paper.id in chosen) chosen - paper.id else chosen + paper.id
                        settings.newsPapers = PAPERS.map { it.id }.filter { it in chosen }
                        Remote.publishSoon(context)
                    }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = paper.id in chosen, onCheckedChange = null)
                    Text(paper.name, color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
            }
            }
        }
    }
}

private fun NewsCategory.label(): Int = when (this) {
    NewsCategory.GENERAL -> R.string.news_cat_general
    NewsCategory.SPORTS -> R.string.news_cat_sports
    NewsCategory.ECONOMY -> R.string.news_cat_economy
    NewsCategory.TECH -> R.string.news_cat_tech
    NewsCategory.SCIENCE -> R.string.news_cat_science
    NewsCategory.HEALTH -> R.string.news_cat_health
    NewsCategory.MOTOR -> R.string.news_cat_motor
    NewsCategory.COOKING -> R.string.news_cat_cooking
    NewsCategory.CULTURE -> R.string.news_cat_culture
}

/** Resúmenes de la portada del panel. */
@Composable
internal fun batterySummary(settings: CaregiverSettings): String =
    if (settings.lowBatteryAlert) stringResource(R.string.cg_battery_sub, settings.lowBatteryPercent) else stringResource(R.string.cg_off)

@Composable
internal fun pillsSummary(store: PillStore): String {
    val times = store.times.size
    return if (!store.enabled || times == 0) stringResource(R.string.cg_off)
    else pluralStringResource(R.plurals.cg_pills_times, times, times)
}

@Composable
internal fun newsSummary(settings: CaregiverSettings): String =
    if (settings.homeLayout.none { it.item == HomeItem.NEWS && it.visible }) stringResource(R.string.cg_off)
    else stringResource(R.string.cg_news_sub, settings.newsPapers.size)
