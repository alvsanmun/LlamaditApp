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
import android.content.ContentUris
import android.content.Intent
import android.provider.ContactsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.contacts.newestContactId
import com.launcherfacil.app.contacts.setHasWhatsApp
import com.launcherfacil.app.contacts.setStarred
import com.launcherfacil.app.contacts.starContactsNewerThan
import com.launcherfacil.app.kiosk.Kiosk
import kotlinx.coroutines.launch

private val TagSos = Color(0xFFC62828)
private val TagVideo = Color(0xFF00796B)
private val TagLocation = Color(0xFF1565C0)

/** Colores suaves para la silueta de quien no tiene foto (siempre el mismo por persona). */
private val SilhouetteColors = listOf(
    Color(0xFFF4B183) to Color(0xFFB5643A),
    Color(0xFF9CC3E6) to Color(0xFF3E6E9C),
    Color(0xFFC5E0B4) to Color(0xFF5E8E45),
    Color(0xFFE2C7F0) to Color(0xFF8C5AA8),
    Color(0xFFD9DEE4) to Color(0xFF9AA4AF),
)

/**
 * Personas (diseño "Cuidador B"): los favoritos con su foto y lo que puede hacer cada
 * uno (SOS, videollamada, pedir la ubicación). Se ordenan arrastrando por el asa de la
 * izquierda; tocar a una persona abre su ficha. "Añadir persona" abre Contactos y, al
 * volver, la nueva persona ya sale aquí (se le pone la estrella sola).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PeoplePage(
    favorites: List<Contact>,
    noPhoto: Set<Long>,
    whatsAppIds: Set<Long>,
    onReorder: (List<Contact>) -> Unit,
    onOpenPerson: (Contact) -> Unit,
    onOpenContacts: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val settings = remember { CaregiverSettings(context) }
    val scope = rememberCoroutineScope()
    val effectiveSos = (favorites.firstOrNull { it.id == settings.sosContactId } ?: favorites.firstOrNull())?.id
    val locateIds = settings.locateContactIds

    // "Añadir persona": al volver de Contactos, los contactos nuevos se marcan como favoritos.
    var addingSince by rememberSaveable { mutableStateOf<Long?>(null) }
    val resumed = rememberOnResume { System.nanoTime() }
    LaunchedEffect(resumed) {
        val since = addingSince ?: return@LaunchedEffect
        addingSince = null
        starContactsNewerThan(context, since)
    }

    // Arrastrar para ordenar: el elegido sigue al dedo y se cambia de sitio con el vecino
    // al pasar la mitad de su altura.
    var order by remember(favorites) { mutableStateOf(favorites) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var rowHeight by remember { mutableIntStateOf(0) }

    CgPage(stringResource(R.string.cg_people), R.drawable.ic_chevron_left, onBack) {
        CgHint(stringResource(R.string.cg_people_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(vertical = 2.dp)) {
            Legend(TagSos, stringResource(R.string.cg_tag_sos))
            Legend(TagVideo, stringResource(R.string.cg_legend_video))
            Legend(TagLocation, stringResource(R.string.cg_legend_location))
        }
        if (order.isEmpty()) CgHint(stringResource(R.string.settings_no_favorites))
        order.forEach { person ->
            key(person.id) {
                val dragging = draggingId == person.id
                PersonCard(
                    person = person,
                    hasPhoto = person.id !in noPhoto,
                    sos = person.id == effectiveSos,
                    video = person.id in whatsAppIds,
                    location = locateIds?.contains(person.id) ?: true,
                    onClick = { onOpenPerson(person) },
                    modifier = Modifier
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            if (dragging) {
                                scaleX = 1.03f
                                scaleY = 1.03f
                            }
                        }
                        .onSizeChanged { rowHeight = it.height },
                    handle = Modifier.pointerInput(person.id) {
                        detectDragGestures(
                            onDragStart = {
                                draggingId = person.id
                                dragOffset = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val step = rowHeight + 10.dp.toPx()
                                val index = order.indexOfFirst { it.id == person.id }
                                if (dragOffset > step / 2 && index < order.lastIndex) {
                                    order = order.toMutableList().apply { add(index + 1, removeAt(index)) }
                                    dragOffset -= step
                                } else if (dragOffset < -step / 2 && index > 0) {
                                    order = order.toMutableList().apply { add(index - 1, removeAt(index)) }
                                    dragOffset += step
                                }
                            },
                            onDragEnd = {
                                draggingId = null
                                dragOffset = 0f
                                settings.contactOrder = order.map { it.id }
                                onReorder(order)
                            },
                            onDragCancel = {
                                draggingId = null
                                dragOffset = 0f
                            }
                        )
                    }
                )
            }
        }
        // Añadir persona: borde discontinuo, como en el diseño.
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .clip(RoundedCornerShape(18.dp))
                .dashedBorder(CgChevron, 18.dp)
                .clickable {
                    scope.launch {
                        addingSince = newestContactId(context)
                        val intent = Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI)
                            .putExtra("finishActivityOnSaveCompleted", true)
                        if (!Kiosk.pauseAndOpen(activity, intent)) {
                            addingSince = null
                            onOpenContacts()
                        }
                    }
                },
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(painterResource(R.drawable.ic_cg_plus), null, tint = CgInk, modifier = Modifier.size(22.dp))
            Text("  " + stringResource(R.string.cg_add_person), color = CgInk, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
        CgHint(stringResource(R.string.cg_add_person_hint))

        CgLabel(stringResource(R.string.settings_google_title))
        CgCard {
            CgHint(stringResource(R.string.settings_google_hint))
            GoogleAccountsSection(onAddGoogleAccount)
        }
        ActionButton(stringResource(R.string.settings_open_contacts), onOpenContacts)
    }
}

/** Borde discontinuo redondeado (botón "Añadir persona"). */
private fun Modifier.dashedBorder(color: Color, radius: Dp) = drawBehind {
    val stroke = 2.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2, stroke / 2),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx())))
    )
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(" $text", color = CgMuted, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonCard(
    person: Contact,
    hasPhoto: Boolean,
    sos: Boolean,
    video: Boolean,
    location: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
    handle: Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(CgCard)
            .clickable(onClick = onClick)
            .padding(start = 0.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // El asa ocupa toda la altura para que sea fácil de coger.
        Box(handle.padding(start = 6.dp).size(width = 30.dp, height = 56.dp), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_drag_handle), null, tint = Color(0xFFA7B1BD))
        }
        PersonPhoto(person, 56.dp, 16.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(person.name, color = CgInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Text("  " + person.phone, color = CgMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (sos) CgTag(stringResource(R.string.cg_tag_sos), TagSos)
                if (video) CgTag(stringResource(R.string.cg_tag_video), TagVideo)
                if (location) CgTag(stringResource(R.string.cg_tag_location), TagLocation)
                if (!hasPhoto) CgTag(stringResource(R.string.cg_tag_nophoto), Color(0xFFFFE0B2), Color(0xFF7A4300))
            }
        }
        Icon(painterResource(R.drawable.ic_chevron_right), null, tint = CgChevron, modifier = Modifier.size(22.dp))
    }
}

/** Foto del contacto o, si no tiene, una silueta sobre un color suave. */
@Composable
internal fun PersonPhoto(person: Contact, size: Dp, corner: Dp) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, person.id) { value = loadContactPhoto(context, person.id) }
    val (bg, fg) = SilhouetteColors[(person.id % SilhouetteColors.size).toInt()]
    Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg), contentAlignment = Alignment.Center) {
        val p = photo
        if (p != null) {
            Image(p, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(painterResource(R.drawable.ic_person), null, tint = fg, modifier = Modifier.fillMaxSize(0.8f))
        }
    }
}

/** Fila con foto pequeña y un control (para elegir el contacto del SOS). */
@Composable
internal fun PersonOptionRow(person: Contact, hasPhoto: Boolean, onClick: () -> Unit, control: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        control()
        PersonPhoto(person, 44.dp, 12.dp)
        Column(Modifier.weight(1f)) {
            Text(person.name, color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(person.phone, color = CgMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        if (!hasPhoto) CgTag(stringResource(R.string.cg_tag_nophoto), Color(0xFFFFE0B2), Color(0xFF7A4300))
    }
}

/**
 * Ficha de una persona: foto en grande y qué puede hacer (SOS, videollamada de
 * WhatsApp, pedir la ubicación). Desde aquí también se cambia su foto o su teléfono
 * en Contactos, o se quita de la lista.
 */
@Composable
internal fun PersonPage(
    person: Contact,
    hasPhoto: Boolean,
    favoriteIds: List<Long>,
    whatsAppIds: Set<Long>,
    onWhatsAppIds: (Set<Long>) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val settings = remember { CaregiverSettings(context) }
    val scope = rememberCoroutineScope()
    var sosId by remember { mutableStateOf(settings.sosContactId) }
    var locateIds by remember { mutableStateOf(settings.locateContactIds) }
    var confirmRemove by remember { mutableStateOf(false) }
    CgPage(person.name, R.drawable.ic_chevron_left, onBack) {
        CgCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PersonPhoto(person, 96.dp, 24.dp)
                Column(Modifier.weight(1f)) {
                    Text(person.name, color = CgInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text(person.phone, color = CgMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    if (!hasPhoto) {
                        Box(Modifier.padding(top = 6.dp)) {
                            CgTag(stringResource(R.string.cg_tag_nophoto), Color(0xFFFFE0B2), Color(0xFF7A4300))
                        }
                    }
                }
            }
            ActionButton(stringResource(R.string.cg_person_edit), {
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.id)
                val intent = Intent(Intent.ACTION_EDIT, uri).putExtra("finishActivityOnSaveCompleted", true)
                Kiosk.pauseAndOpen(activity, intent)
            })
        }

        CgLabel(stringResource(R.string.cg_person_can))
        CgCard {
            // Sin contacto elegido, el SOS llama al primer favorito.
            val effectiveSos = sosId?.takeIf { it in favoriteIds } ?: favoriteIds.firstOrNull()
            val isSos = person.id == effectiveSos
            SwitchRow(stringResource(R.string.cg_person_sos), isSos) {
                if (it) {
                    sosId = person.id
                    settings.sosContactId = person.id
                }
            }
            CgHint(stringResource(R.string.cg_person_sos_hint))
            SwitchRow(stringResource(R.string.cg_person_video), person.id in whatsAppIds) { on ->
                scope.launch {
                    if (setHasWhatsApp(context, person, on)) {
                        onWhatsAppIds(if (on) whatsAppIds + person.id else whatsAppIds - person.id)
                    }
                }
            }
            val canLocate = locateIds?.contains(person.id) ?: true
            SwitchRow(stringResource(R.string.cg_person_location), canLocate) { on ->
                // Sin elegir (null) pueden todos los favoritos.
                val base = locateIds ?: favoriteIds.toSet()
                val updated = if (on) base + person.id else base - person.id
                locateIds = updated
                settings.locateContactIds = updated
            }
        }
        SmsCostNote()

        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(2.dp, RojoSos, RoundedCornerShape(14.dp))
                .clickable { confirmRemove = true },
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.cg_person_remove), color = RojoSos, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            text = { Text(stringResource(R.string.cg_person_remove_confirm, person.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    scope.launch {
                        setStarred(context, person.id, false)
                        onBack()
                    }
                }) { Text(stringResource(R.string.cg_person_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
