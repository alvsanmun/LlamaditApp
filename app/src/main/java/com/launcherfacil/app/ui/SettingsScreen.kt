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

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.contactsWithWhatsApp
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.setHasWhatsApp
import com.launcherfacil.app.inbox.rememberContentVersion
import kotlinx.coroutines.launch

/** Configuración del cuidador. Aquí sí hay texto: no la usa la persona. */
@Composable
fun SettingsScreen(
    isDeviceOwner: Boolean,
    onChangePin: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    whatsAppAccess: Boolean,
    onOpenNotificationAccess: () -> Unit,
    kioskPaused: Boolean,
    onPauseKiosk: () -> Unit,
    onResumeKiosk: () -> Unit,
    onDisableKiosk: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var favorites by remember { mutableStateOf(emptyList<Contact>()) }
    // Se recarga al cambiar la agenda: por ejemplo, al volver de Contactos tras añadir
    // un favorito (antes el panel seguía diciendo que no había ninguno).
    val contactsVersion = rememberContentVersion(ContactsContract.Contacts.CONTENT_URI)
    LaunchedEffect(contactsVersion) { favorites = loadFavoriteContacts(context) }

    // Sube o baja un favorito y guarda el orden (Llamar y Videollamada usan el mismo).
    fun move(index: Int, by: Int) {
        val target = index + by
        if (target !in favorites.indices) return
        favorites = favorites.toMutableList().apply { add(target, removeAt(index)) }
        settings.contactOrder = favorites.map { it.id }
    }

    var sosId by remember { mutableStateOf(settings.sosContactId) }
    var locateIds by remember { mutableStateOf(settings.locateContactIds) }
    var keywords by remember { mutableStateOf(settings.keywords.joinToString(", ")) }
    var confirmDisable by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var whatsAppIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(contactsVersion) { whatsAppIds = contactsWithWhatsApp(context) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF90CAF9))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Fondo)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            HomeBar(onHome)
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Section(stringResource(R.string.settings_order_title))
                Hint(stringResource(R.string.settings_order_hint))
                favorites.forEachIndexed { index, contact ->
                    OrderRow(
                        title = contact.name,
                        subtitle = contact.phone,
                        canMoveUp = index > 0,
                        canMoveDown = index < favorites.lastIndex,
                        onMoveUp = { move(index, -1) },
                        onMoveDown = { move(index, +1) }
                    )
                }

                Section(stringResource(R.string.settings_sos_title))
                if (favorites.isEmpty()) Hint(stringResource(R.string.settings_no_favorites))
                // Si no se ha elegido, se usa el primer favorito.
                val effectiveSos = favorites.firstOrNull { it.id == sosId } ?: favorites.firstOrNull()
                favorites.forEach { contact ->
                    OptionRow(contact.name, contact.phone, onClick = {
                        sosId = contact.id
                        settings.sosContactId = contact.id
                    }) {
                        RadioButton(selected = contact == effectiveSos, onClick = null)
                    }
                }

                Section(stringResource(R.string.settings_locate_title))
                favorites.forEach { contact ->
                    val checked = locateIds?.contains(contact.id) ?: true
                    OptionRow(contact.name, contact.phone, onClick = {
                        val current = locateIds ?: favorites.map { it.id }.toSet()
                        val updated = if (checked) current - contact.id else current + contact.id
                        locateIds = updated
                        settings.locateContactIds = updated
                    }) {
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }

                Section(stringResource(R.string.settings_whatsapp_title))
                Hint(stringResource(R.string.settings_whatsapp_hint))
                favorites.forEach { contact ->
                    val checked = contact.id in whatsAppIds
                    OptionRow(contact.name, contact.phone, onClick = {
                        scope.launch {
                            if (setHasWhatsApp(context, contact, !checked)) {
                                whatsAppIds = if (checked) whatsAppIds - contact.id else whatsAppIds + contact.id
                            }
                        }
                    }) {
                        Checkbox(checked = checked, onCheckedChange = null)
                    }
                }

                Section(stringResource(R.string.settings_keywords_title))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        settings.keywords = it.split(",")
                    },
                    supportingText = { Text(stringResource(R.string.settings_keywords_hint)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Section(stringResource(R.string.settings_wa_calls_title))
                Hint(
                    if (whatsAppAccess) stringResource(R.string.settings_wa_calls_on)
                    else stringResource(R.string.settings_wa_calls_off)
                )
                if (!whatsAppAccess) ActionButton(stringResource(R.string.settings_wa_calls_enable), onOpenNotificationAccess)

                Section(stringResource(R.string.settings_security_title))
                ActionButton(stringResource(R.string.settings_change_pin), onChangePin)

                Section(stringResource(R.string.settings_other_apps_title))
                Hint(
                    when {
                        !isDeviceOwner -> stringResource(R.string.settings_not_device_owner)
                        kioskPaused -> stringResource(R.string.settings_kiosk_paused_short)
                        else -> stringResource(R.string.settings_leaves_kiosk)
                    }
                )
                ActionButton(stringResource(R.string.settings_open_contacts), onOpenContacts)
                ActionButton(stringResource(R.string.settings_open_android_settings), onOpenAndroidSettings)

                if (isDeviceOwner) {
                    Section(stringResource(R.string.settings_kiosk_title))
                    if (kioskPaused) {
                        Hint(stringResource(R.string.settings_kiosk_paused_hint))
                        ActionButton(stringResource(R.string.settings_kiosk_resume), onResumeKiosk, color = VerdeLlamar)
                    } else {
                        Hint(stringResource(R.string.settings_kiosk_active_hint))
                        ActionButton(stringResource(R.string.settings_kiosk_pause), onPauseKiosk)
                    }
                    Spacer(Modifier.height(16.dp))
                    Hint(stringResource(R.string.settings_remove_owner_hint))
                    ActionButton(stringResource(R.string.settings_remove_owner), { confirmDisable = true }, color = RojoSos)
                }

                // Avisos legales que pide la licencia (GNU AGPL v3, sección 5 d).
                Section(stringResource(R.string.settings_about_title))
                Text("${stringResource(R.string.app_name)} ${appVersion(context)}", color = TextoClaro, fontSize = 18.sp)
                Hint("Copyright (C) 2026 alvsanmun")
                Hint(stringResource(R.string.settings_license))
                Hint(stringResource(R.string.settings_source, SOURCE_URL))
                Spacer(Modifier.height(24.dp))
            }
        }

        if (confirmDisable) {
            AlertDialog(
                onDismissRequest = { confirmDisable = false },
                title = { Text(stringResource(R.string.remove_owner_dialog_title)) },
                text = {
                    Text(stringResource(R.string.remove_owner_dialog_text))
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDisable = false
                        onDisableKiosk()
                    }) { Text(stringResource(R.string.remove_owner_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDisable = false }) { Text(stringResource(R.string.cancel)) }
                }
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, color = TextoClaro, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    HorizontalDivider(color = GrisBoton)
}

@Composable
private fun Hint(text: String) {
    Text(text, color = TextoSuave, fontSize = 16.sp)
}

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        control()
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, color = TextoClaro, fontSize = 18.sp)
            Text(subtitle, color = TextoSuave, fontSize = 14.sp)
        }
    }
}

@Composable
private fun OrderRow(
    title: String,
    subtitle: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextoClaro, fontSize = 18.sp)
            Text(subtitle, color = TextoSuave, fontSize = 14.sp)
        }
        MoveButton(R.drawable.ic_arrow_up, canMoveUp, onMoveUp)
        Spacer(Modifier.width(8.dp))
        MoveButton(R.drawable.ic_arrow_down, canMoveDown, onMoveDown)
    }
}

@Composable
private fun MoveButton(@DrawableRes icon: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = GrisBoton,
            contentColor = TextoClaro
        ),
        modifier = Modifier.size(52.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit, color: Color = GrisBoton) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = TextoClaro
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text, fontSize = 18.sp)
    }
}

/** Repositorio con el código fuente (la licencia AGPL obliga a ofrecerlo). */
private const val SOURCE_URL = "https://github.com/alvsanmun/LlamaditApp"

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: PackageManager.NameNotFoundException) {
    ""
}
