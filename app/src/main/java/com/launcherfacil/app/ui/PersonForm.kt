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
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.PhotoChange
import com.launcherfacil.app.contacts.createPerson
import com.launcherfacil.app.contacts.draftPhotoFile
import com.launcherfacil.app.contacts.findContactByNumber
import com.launcherfacil.app.contacts.isValidPhone
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.contacts.loadDraftPhoto
import com.launcherfacil.app.contacts.loadGoogleAccounts
import com.launcherfacil.app.contacts.loadOtherContacts
import com.launcherfacil.app.contacts.saveDraftPhoto
import com.launcherfacil.app.contacts.setHasWhatsApp
import com.launcherfacil.app.contacts.setStarred
import com.launcherfacil.app.contacts.updatePerson
import com.launcherfacil.app.gohome.cameraUri
import com.launcherfacil.app.kiosk.Kiosk
import kotlinx.coroutines.launch

private const val CAMERA_FILE = "person.jpg"

/**
 * Formulario para añadir una persona ([person] = null) o cambiar sus datos, sin salir
 * de LlamaditApp: foto, nombre, teléfono y, al añadir, dónde se guarda y si tiene
 * WhatsApp. La persona nueva se guarda con la estrella, así sale ya en la app.
 * [onSaved] recibe el id del contacto.
 */
@Composable
internal fun PersonFormPage(
    person: Contact?,
    hasWhatsApp: Boolean,
    onPickExisting: () -> Unit,
    onSaved: (Long) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf(person?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(person?.phone.orEmpty()) }
    var photoChange by rememberSaveable { mutableStateOf(PhotoChange.KEEP) }
    var photoVersion by rememberSaveable { mutableIntStateOf(0) }
    var whatsApp by rememberSaveable { mutableStateOf(hasWhatsApp) }
    var account by rememberSaveable { mutableStateOf<String?>(null) }
    var accountChosen by rememberSaveable { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var photoFailed by remember { mutableStateOf(false) }

    // Al abrir el formulario se olvida la foto que quedara de otra vez.
    LaunchedEffect(Unit) { if (photoVersion == 0) draftPhotoFile(context).delete() }

    val accounts by produceState(emptyList<String>()) { value = loadGoogleAccounts(context).map { it.name } }
    // Por defecto, en la primera cuenta de Google (así se ve también en contacts.google.com).
    LaunchedEffect(accounts) {
        if (!accountChosen && accounts.isNotEmpty()) account = accounts.first()
    }
    val shownPhoto by produceState<Bitmap?>(null, photoVersion, photoChange) {
        value = when (photoChange) {
            PhotoChange.REPLACE -> loadDraftPhoto(context)
            else -> null
        }
    }
    val currentPhoto by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, person?.id) {
        value = person?.let { loadContactPhoto(context, it.id) }
    }

    // Si el teléfono ya está en la agenda, se ofrece añadir a esa persona en vez de repetirla.
    val duplicate by produceState<Contact?>(null, phone, person?.id) {
        value = if (person == null && isValidPhone(phone)) findContactByNumber(context, phone.trim()) else null
    }

    fun gotPhoto(uri: Uri) = scope.launch {
        photoFailed = !saveDraftPhoto(context, uri)
        if (!photoFailed) {
            photoChange = PhotoChange.REPLACE
            photoVersion++
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) gotPhoto(cameraUri(context, CAMERA_FILE))
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) gotPhoto(uri)
    }
    // La cámara y la galería son otras apps: se sale un momento del quiosco.
    fun leaveKiosk() {
        Kiosk.unlock(activity)
    }

    val nameError = showErrors && name.isBlank()
    val phoneError = showErrors && !isValidPhone(phone)

    fun save() {
        showErrors = true
        failed = false
        if (name.isBlank() || !isValidPhone(phone) || saving) return
        saving = true
        scope.launch {
            val photo = if (photoChange == PhotoChange.REPLACE) loadDraftPhoto(context) else null
            val id = if (person == null) {
                createPerson(context, name, phone, photo, account)
            } else {
                person.id.takeIf {
                    updatePerson(context, it, person.phone, name, phone, photoChange, photo)
                }
            }
            if (id != null) {
                // La fila de la videollamada lleva el número: se rehace si ha cambiado.
                val saved = Contact(id, name.trim(), phone.trim())
                if (person == null) {
                    if (whatsApp) setHasWhatsApp(context, saved, true)
                } else if (hasWhatsApp && phone.trim() != person.phone.trim()) {
                    setHasWhatsApp(context, person, false)
                    setHasWhatsApp(context, saved, true)
                }
                draftPhotoFile(context).delete()
                onSaved(id)
            } else {
                failed = true
            }
            saving = false
        }
    }

    CgPage(
        stringResource(if (person == null) R.string.cg_form_new_title else R.string.cg_form_edit_title),
        R.drawable.ic_chevron_left,
        onBack
    ) {
        CgLabel(stringResource(R.string.cg_form_photo))
        CgCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(112.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFFD9DEE4)),
                    contentAlignment = Alignment.Center
                ) {
                    val draft = shownPhoto
                    val existing = currentPhoto.takeIf { photoChange == PhotoChange.KEEP }
                    when {
                        draft != null -> Image(draft.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        existing != null -> Image(existing, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        else -> Icon(painterResource(R.drawable.ic_person), null, tint = Color(0xFF9AA4AF), modifier = Modifier.fillMaxSize(0.8f))
                    }
                }
                Box(Modifier.weight(1f)) { CgHint(stringResource(R.string.cg_form_photo_hint)) }
            }
            if (photoFailed) CgWarning(stringResource(R.string.settings_house_photo_failed))
            ActionButton(stringResource(R.string.cg_form_camera), {
                leaveKiosk()
                try {
                    camera.launch(cameraUri(context, CAMERA_FILE))
                } catch (e: ActivityNotFoundException) {
                    photoFailed = true
                }
            })
            ActionButton(stringResource(R.string.cg_form_gallery), {
                leaveKiosk()
                gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            })
            val hasAnyPhoto = photoChange == PhotoChange.REPLACE || (photoChange == PhotoChange.KEEP && currentPhoto != null)
            if (hasAnyPhoto) {
                ActionButton(stringResource(R.string.cg_form_photo_remove), {
                    photoChange = if (person == null) PhotoChange.KEEP else PhotoChange.REMOVE
                    draftPhotoFile(context).delete()
                    photoVersion++
                })
            }
        }

        CgLabel(stringResource(R.string.cg_form_data))
        CgCard {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.cg_form_name)) },
                isError = nameError,
                supportingText = if (nameError) ({ Text(stringResource(R.string.cg_form_error_name)) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { v -> phone = v.filter { it.isDigit() || it in "+ " } },
                label = { Text(stringResource(R.string.cg_form_phone)) },
                isError = phoneError,
                supportingText = {
                    Text(stringResource(if (phoneError) R.string.cg_form_error_phone else R.string.cg_form_phone_hint))
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
            duplicate?.let { other ->
                CgWarning(stringResource(R.string.cg_form_duplicate, other.name))
                ActionButton(stringResource(R.string.cg_form_duplicate_add, other.name), {
                    scope.launch {
                        if (setStarred(context, other.id, true)) onSaved(other.id)
                    }
                })
            }
        }

        if (person == null) {
            CgCard {
                SwitchRow(stringResource(R.string.cg_person_video), whatsApp) { whatsApp = it }
                CgHint(stringResource(R.string.cg_form_whatsapp_hint))
            }
            if (accounts.isNotEmpty()) {
                CgLabel(stringResource(R.string.cg_form_account))
                CgCard {
                    (accounts + listOf<String?>(null)).forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    account = option
                                    accountChosen = true
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = account == option, onClick = null)
                            Text(
                                "  " + (option ?: stringResource(R.string.cg_form_account_local)),
                                color = CgInk, fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    CgHint(stringResource(R.string.cg_form_account_hint))
                }
            }
        }

        if (failed) CgWarning(stringResource(R.string.cg_form_failed))
        ActionButton(
            stringResource(if (saving) R.string.cg_form_saving else R.string.cg_form_save),
            ::save,
            color = Color(0xFF2E7D32)
        )
        if (person == null) {
            ActionButton(stringResource(R.string.cg_pick_existing), onPickExisting)
        }
    }
}

/**
 * Elegir a alguien que ya está en la agenda (sin estrella) para añadirlo a la lista,
 * con un buscador por nombre o teléfono.
 */
@Composable
internal fun PickExistingPage(onPicked: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val all by produceState<List<Contact>?>(null) { value = loadOtherContacts(context) }
    CgPage(stringResource(R.string.cg_pick_existing), R.drawable.ic_chevron_left, onBack) {
        CgHint(stringResource(R.string.cg_pick_existing_hint))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.cg_pick_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val list = all ?: return@CgPage
        val digits = query.filter { it.isDigit() }
        val shown = list.filter {
            query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) ||
                (digits.length >= 3 && it.phone.filter { c -> c.isDigit() }.contains(digits))
        }
        if (shown.isEmpty()) CgHint(stringResource(R.string.cg_pick_none))
        CgCard {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                shown.take(200).forEach { contact ->
                    PersonOptionRow(contact, hasPhoto = true, onClick = {
                        scope.launch { if (setStarred(context, contact.id, true)) onPicked(contact.id) }
                    }) {
                        Icon(painterResource(R.drawable.ic_cg_plus), null, tint = CgAccent, modifier = Modifier.padding(start = 4.dp).size(24.dp))
                    }
                }
            }
        }
    }
}
