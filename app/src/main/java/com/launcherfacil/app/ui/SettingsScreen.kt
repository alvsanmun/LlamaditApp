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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.contactsWithWhatsApp
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.setHasWhatsApp
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
    LaunchedEffect(Unit) { favorites = loadFavoriteContacts(context) }

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
    LaunchedEffect(Unit) { whatsAppIds = contactsWithWhatsApp(context) }

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
                Section("Orden de los contactos")
                Hint("El mismo orden en Llamar y en Videollamada. Los favoritos nuevos salen al final.")
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

                Section("Contacto para el SOS")
                if (favorites.isEmpty()) Hint("No hay contactos favoritos (★).")
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

                Section("Pueden pedir la ubicación por SMS")
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

                Section("Tienen WhatsApp (videollamada)")
                Hint("Márcalo si esa persona usa WhatsApp: aparecerá en el botón de videollamada.")
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

                Section("Palabras clave para pedir la ubicación")
                OutlinedTextField(
                    value = keywords,
                    onValueChange = {
                        keywords = it
                        settings.keywords = it.split(",")
                    },
                    supportingText = { Text("Separadas por comas. Da igual mayúsculas, tildes o signos.") },
                    modifier = Modifier.fillMaxWidth()
                )

                Section("Llamadas de WhatsApp")
                Hint(
                    if (whatsAppAccess) "Activado: las llamadas de WhatsApp se ven con la pantalla grande."
                    else "Desactivado: hay que dar \"acceso a notificaciones\" a LlamaditApp."
                )
                if (!whatsAppAccess) ActionButton("Activar acceso a notificaciones", onOpenNotificationAccess)

                Section("Seguridad")
                ActionButton("Cambiar el PIN", onChangePin)

                Section("Otras apps")
                Hint(
                    when {
                        !isDeviceOwner -> "La app no es Device Owner: el modo quiosco no está activo."
                        kioskPaused -> "El modo quiosco está en pausa."
                        else -> "Se sale del modo quiosco hasta volver a esta app."
                    }
                )
                ActionButton("Abrir Contactos (favoritos y fotos)", onOpenContacts)
                ActionButton("Abrir Ajustes de Android", onOpenAndroidSettings)

                if (isDeviceOwner) {
                    Section("Modo quiosco")
                    if (kioskPaused) {
                        Hint("En pausa: el móvil se usa con normalidad (también tras reiniciar). Actívalo al terminar.")
                        ActionButton("Activar el modo quiosco", onResumeKiosk, color = VerdeLlamar)
                    } else {
                        Hint("Activo. Pausarlo permite instalar o configurar cosas; se vuelve a activar aquí, sin ordenador.")
                        ActionButton("Pausar el modo quiosco", onPauseKiosk)
                    }
                    Spacer(Modifier.height(16.dp))
                    Hint("Solo para desinstalar el launcher o entregar el móvil:")
                    ActionButton("Quitar el Device Owner", { confirmDisable = true }, color = RojoSos)
                }

                // Avisos legales que pide la licencia (GNU AGPL v3, sección 5 d).
                Section("Acerca de")
                Text("LlamaditApp ${appVersion(context)}", color = TextoClaro, fontSize = 18.sp)
                Hint("Copyright (C) 2026 alvsanmun")
                Hint(
                    "Software libre: puedes redistribuirlo y modificarlo bajo los términos de la " +
                        "licencia GNU Affero General Public License, versión 3 o posterior. " +
                        "Se distribuye SIN NINGUNA GARANTÍA."
                )
                Hint("Código fuente y licencia: $SOURCE_URL")
                Spacer(Modifier.height(24.dp))
            }
        }

        if (confirmDisable) {
            AlertDialog(
                onDismissRequest = { confirmDisable = false },
                title = { Text("¿Quitar el Device Owner?") },
                text = {
                    Text(
                        "Se desactiva el modo quiosco para siempre. Para volver a activarlo " +
                            "hace falta un ordenador (adb) y quitar antes todas las cuentas del " +
                            "teléfono (Google, WhatsApp...). Si solo quieres instalar o configurar " +
                            "algo, usa \"Pausar el modo quiosco\"."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDisable = false
                        onDisableKiosk()
                    }) { Text("Quitar") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDisable = false }) { Text("Cancelar") }
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
