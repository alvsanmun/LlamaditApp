package com.launcherfacil.app.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadFavoriteContacts

/** Configuración del cuidador. Aquí sí hay texto: no la usa la persona. */
@Composable
fun SettingsScreen(
    isDeviceOwner: Boolean,
    onChangePin: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    whatsAppAccess: Boolean,
    onOpenNotificationAccess: () -> Unit,
    onDisableKiosk: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    val favorites by produceState<List<Contact>>(emptyList()) { value = loadFavoriteContacts(context) }

    var sosId by remember { mutableStateOf(settings.sosContactId) }
    var locateIds by remember { mutableStateOf(settings.locateContactIds) }
    var keywords by remember { mutableStateOf(settings.keywords.joinToString(", ")) }
    var confirmDisable by remember { mutableStateOf(false) }

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
                    else "Desactivado: hay que dar \"acceso a notificaciones\" a Launcher Fácil."
                )
                if (!whatsAppAccess) ActionButton("Activar acceso a notificaciones", onOpenNotificationAccess)

                Section("Seguridad")
                ActionButton("Cambiar el PIN", onChangePin)

                Section("Otras apps")
                Hint(
                    if (isDeviceOwner) "Se sale del modo quiosco hasta volver a esta app."
                    else "La app no es Device Owner: el modo quiosco no está activo."
                )
                ActionButton("Abrir Contactos (favoritos y fotos)", onOpenContacts)
                ActionButton("Abrir Ajustes de Android", onOpenAndroidSettings)

                if (isDeviceOwner) {
                    Section("Modo quiosco")
                    ActionButton("Desactivar el modo quiosco", { confirmDisable = true }, danger = true)
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        if (confirmDisable) {
            AlertDialog(
                onDismissRequest = { confirmDisable = false },
                title = { Text("¿Desactivar el modo quiosco?") },
                text = {
                    Text(
                        "La persona podrá salir del launcher y usar todo el teléfono. " +
                            "Para volver a activarlo hace falta un ordenador (adb) y que el " +
                            "teléfono no tenga cuentas de Google ni de Samsung."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDisable = false
                        onDisableKiosk()
                    }) { Text("Desactivar") }
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
private fun ActionButton(text: String, onClick: () -> Unit, danger: Boolean = false) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (danger) RojoSos else GrisBoton,
            contentColor = TextoClaro
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        Text(text, fontSize = 18.sp)
    }
}
