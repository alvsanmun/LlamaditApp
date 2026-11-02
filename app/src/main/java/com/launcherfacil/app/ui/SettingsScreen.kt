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
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.BackButton
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.config.HomeItem
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.contactsWithWhatsApp
import com.launcherfacil.app.contacts.favoritesWithoutPhoto
import com.launcherfacil.app.contacts.isWhatsAppInstalled
import com.launcherfacil.app.contacts.loadFavoriteContacts
import com.launcherfacil.app.contacts.setHasWhatsApp
import com.launcherfacil.app.exercises.Exercise
import com.launcherfacil.app.exercises.ExerciseProgress
import com.launcherfacil.app.gohome.hasPortalPhoto
import com.launcherfacil.app.inbox.rememberContentVersion
import com.launcherfacil.app.kiosk.Kiosk
import com.launcherfacil.app.radio.RadioStations
import com.launcherfacil.app.whatsapp.WhatsAppHomeButton
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.launch

/** [formId] del formulario para añadir una persona (los ids de contacto empiezan en 1). */
private const val NEW_PERSON = -1L

/** Apartados del panel, en el orden de la portada. */
internal enum class CgSection(val color: Color, @DrawableRes val icon: Int) {
    PEOPLE(Color(0xFF2E7D32), R.drawable.ic_cg_people),
    SOS(Color(0xFFC62828), R.drawable.ic_cg_sos),
    HOME(Color(0xFF1565C0), R.drawable.ic_cg_grid),
    LOCATE(Color(0xFF00796B), R.drawable.ic_cg_location),
    HOUSE(Color(0xFF795548), R.drawable.ic_cg_house),
    EXERCISES(Color(0xFFAD1457), R.drawable.ic_cg_puzzle),
    RADIO(Color(0xFF3949AB), R.drawable.ic_radio),
    WHATSAPP(Color(0xFF43A047), R.drawable.ic_cg_chat),
    CONNECTION(Color(0xFF455A64), R.drawable.ic_cg_wifi),
    SECURITY(Color(0xFF37474F), R.drawable.ic_cg_lock),
}

/**
 * Configuración del cuidador (diseño "Cuidador A"): una portada con el estado del modo
 * quiosco y la lista de apartados, cada uno con un resumen de cómo está (en ámbar si
 * falta algo). Cada apartado se abre en su propia página. Aquí sí hay texto: no la usa
 * la persona.
 */
@Composable
fun SettingsScreen(
    isDeviceOwner: Boolean,
    onChangePin: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onAddGoogleAccount: () -> Unit,
    onAllowOverlay: () -> Unit,
    onOpenWifiSettings: () -> Unit,
    onOpenHousePin: (Double, Double) -> Unit,
    onTestRoute: (Double, Double) -> Unit,
    whatsAppAccess: Boolean,
    onOpenNotificationAccess: () -> Unit,
    kioskPaused: Boolean,
    onPauseKiosk: () -> Unit,
    onResumeKiosk: () -> Unit,
    backButton: BackButton,
    onBackButtonChange: (BackButton) -> Unit,
    onDisableKiosk: () -> Unit,
    onInstallWhatsApp: () -> Unit,
    onEndCaregiverSession: () -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var section by rememberSaveable { mutableStateOf<CgSection?>(null) }
    var personId by rememberSaveable { mutableStateOf<Long?>(null) }
    // Formulario de persona: NEW_PERSON para añadir, o el id de la persona que se cambia.
    var formId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickingExisting by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = section != null) {
        when {
            pickingExisting -> pickingExisting = false
            formId != null -> formId = null
            personId != null -> personId = null
            else -> section = null
        }
    }

    // Favoritos y lo que depende de ellos: se recargan al cambiar la agenda (por ejemplo,
    // al volver de Contactos) y al cambiar de página.
    val contactsVersion = rememberContentVersion(ContactsContract.Contacts.CONTENT_URI)
    var favorites by remember { mutableStateOf(emptyList<Contact>()) }
    var noPhoto by remember { mutableStateOf(emptySet<Long>()) }
    var whatsAppIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(contactsVersion, section, personId, formId) {
        favorites = loadFavoriteContacts(context)
        noPhoto = favoritesWithoutPhoto(context)
        whatsAppIds = contactsWithWhatsApp(context)
    }

    CaregiverTheme {
        val person = favorites.firstOrNull { it.id == personId }
        // Al guardar, se abre la ficha de la persona (para elegir SOS, ubicación…).
        val onSaved = { id: Long ->
            pickingExisting = false
            formId = null
            personId = id
        }
        when {
            pickingExisting -> PickExistingPage(onPicked = onSaved, onBack = { pickingExisting = false })
            formId == NEW_PERSON -> PersonFormPage(
                person = null,
                hasWhatsApp = false,
                onPickExisting = { pickingExisting = true },
                onSaved = onSaved,
                onBack = { formId = null }
            )
            formId != null && person != null -> PersonFormPage(
                person = person,
                hasWhatsApp = person.id in whatsAppIds,
                onPickExisting = {},
                onSaved = onSaved,
                onBack = { formId = null }
            )
            personId != null && person != null -> PersonPage(
                person = person,
                hasPhoto = person.id !in noPhoto,
                favoriteIds = favorites.map { it.id },
                whatsAppIds = whatsAppIds,
                onWhatsAppIds = { whatsAppIds = it },
                onEdit = { formId = person.id },
                onBack = { personId = null }
            )
            section == null -> SettingsHome(
                isDeviceOwner = isDeviceOwner,
                kioskPaused = kioskPaused,
                favorites = favorites,
                noPhoto = noPhoto,
                onPauseKiosk = onPauseKiosk,
                onResumeKiosk = onResumeKiosk,
                onEndCaregiverSession = onEndCaregiverSession,
                onOpen = { section = it },
                onHome = onHome
            )
            else -> {
                val current = section!!
                val back = { section = null }
                when (current) {
                    CgSection.PEOPLE -> PeoplePage(
                        favorites = favorites,
                        noPhoto = noPhoto,
                        whatsAppIds = whatsAppIds,
                        onReorder = { favorites = it },
                        onOpenPerson = { personId = it.id },
                        onAddPerson = { formId = NEW_PERSON },
                        onOpenContacts = onOpenContacts,
                        onAddGoogleAccount = onAddGoogleAccount,
                        onBack = back
                    )
                    CgSection.SOS -> SosPage(favorites, noPhoto, back)
                    CgSection.HOME -> HomePage(backButton, onBackButtonChange, back)
                    CgSection.LOCATE -> LocatePage(favorites, back)
                    CgSection.HOUSE -> CgPage(stringResource(R.string.settings_house_title), R.drawable.ic_chevron_left, back) {
                        CgCard {
                            CgHint(stringResource(R.string.settings_house_hint))
                            HouseEditor(onOpenHousePin, onTestRoute)
                        }
                    }
                    CgSection.EXERCISES -> CgPage(stringResource(R.string.settings_exercises_title), R.drawable.ic_chevron_left, back) {
                        CgCard {
                            CgHint(stringResource(R.string.settings_exercises_hint))
                            ExercisesSection()
                        }
                    }
                    CgSection.RADIO -> CgPage(stringResource(R.string.settings_radio_title), R.drawable.ic_chevron_left, back) {
                        CgCard {
                            CgHint(stringResource(R.string.settings_radio_hint))
                            RadioSection()
                        }
                    }
                    CgSection.WHATSAPP -> WhatsAppPage(
                        favorites, whatsAppIds, { whatsAppIds = it },
                        onInstallWhatsApp, onAllowOverlay, onOpenNotificationAccess, back
                    )
                    CgSection.CONNECTION -> ConnectionPage(isDeviceOwner, onOpenWifiSettings, back)
                    CgSection.SECURITY -> SecurityPage(
                        isDeviceOwner, kioskPaused, onChangePin, onOpenContacts, onOpenAndroidSettings,
                        onPauseKiosk, onResumeKiosk, onDisableKiosk, back
                    )
                }
            }
        }
    }
}

/** Portada: estado del modo quiosco y apartados con su resumen. */
@Composable
private fun SettingsHome(
    isDeviceOwner: Boolean,
    kioskPaused: Boolean,
    favorites: List<Contact>,
    noPhoto: Set<Long>,
    onPauseKiosk: () -> Unit,
    onResumeKiosk: () -> Unit,
    onEndCaregiverSession: () -> Unit,
    onOpen: (CgSection) -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    // Lo que puede cambiar fuera de la app (permisos, WhatsApp instalado) se relee al volver.
    val resumed = rememberOnResume { System.nanoTime() }
    val inSession = remember(resumed) {
        Kiosk.touchCaregiverSession(context)
        Kiosk.inCaregiverSession(context)
    }
    val notificationAccess = remember(resumed) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val whatsAppInstalled = remember(resumed) { context.isWhatsAppInstalled() }
    val overlayAllowed = remember(resumed) { WhatsAppHomeButton.canShow(context) }
    val layout = remember(resumed) { settings.homeLayout }
    fun visible(item: HomeItem) = layout.any { it.item == item && it.visible }

    // Resumen de cada apartado: texto y si hay algo que revisar.
    val sosContact = favorites.firstOrNull { it.id == settings.sosContactId } ?: favorites.firstOrNull()
    val locateCount = settings.locateContactIds?.let { ids -> favorites.count { it.id in ids } } ?: favorites.size
    val house = settings.housePosition
    val exercises = remember(resumed) { ExerciseProgress(context).enabled().size }
    val stations = remember(resumed) { RadioStations(context).stations.size }
    val missingPhotos = favorites.count { it.id in noPhoto }

    val rows: List<Triple<CgSection, String, Boolean>> = listOf(
        Triple(
            CgSection.PEOPLE,
            when {
                favorites.isEmpty() -> stringResource(R.string.cg_people_none)
                missingPhotos > 0 -> stringResource(R.string.cg_people_sub, favorites.size) + " · " +
                    stringResource(R.string.cg_people_nophoto, missingPhotos)
                else -> stringResource(R.string.cg_people_sub, favorites.size)
            },
            favorites.isEmpty() || missingPhotos > 0
        ),
        Triple(
            CgSection.SOS,
            sosContact?.let { stringResource(R.string.cg_sos_sub, it.name) } ?: stringResource(R.string.cg_sos_none),
            sosContact == null
        ),
        Triple(
            CgSection.HOME,
            stringResource(
                R.string.cg_home_sub,
                layout.count { it.item.isButton && it.visible },
                stringResource(if (settings.backButton == BackButton.HOUSE_TOP) R.string.cg_back_house else R.string.cg_back_arrow)
            ),
            false
        ),
        Triple(
            CgSection.LOCATE,
            if (locateCount == 0) stringResource(R.string.cg_locate_none) else stringResource(R.string.cg_locate_sub, locateCount),
            false
        ),
        Triple(
            CgSection.HOUSE,
            when {
                !visible(HomeItem.GO_HOME) && house == null -> stringResource(R.string.cg_off)
                house == null -> stringResource(R.string.cg_house_nohouse)
                !hasPortalPhoto(context) -> stringResource(R.string.cg_house_nophoto)
                else -> settings.houseAddress.ifBlank { stringResource(R.string.cg_house_ok) }
            },
            visible(HomeItem.GO_HOME) && (house == null || !hasPortalPhoto(context))
        ),
        Triple(
            CgSection.EXERCISES,
            if (!visible(HomeItem.EXERCISES)) stringResource(R.string.cg_off)
            else stringResource(R.string.cg_exercises_sub, exercises, Exercise.entries.size),
            false
        ),
        Triple(
            CgSection.RADIO,
            if (!visible(HomeItem.RADIO)) stringResource(R.string.cg_off) else stringResource(R.string.cg_radio_sub, stations),
            visible(HomeItem.RADIO) && stations == 0
        ),
        Triple(
            CgSection.WHATSAPP,
            when {
                !whatsAppInstalled -> stringResource(R.string.cg_wa_missing)
                !notificationAccess -> stringResource(R.string.cg_wa_noaccess)
                visible(HomeItem.WHATSAPP_APP) && !overlayAllowed -> stringResource(R.string.cg_wa_nooverlay)
                else -> stringResource(R.string.cg_wa_ok)
            },
            (visible(HomeItem.WHATSAPP) || visible(HomeItem.WHATSAPP_APP)) &&
                (!whatsAppInstalled || !notificationAccess || (visible(HomeItem.WHATSAPP_APP) && !overlayAllowed))
        ),
        Triple(
            CgSection.CONNECTION,
            stringResource(if (settings.wifiEnabled) R.string.cg_conn_wifi else R.string.cg_conn_mobile),
            false
        ),
        Triple(CgSection.SECURITY, stringResource(R.string.cg_security_sub), false),
    )
    val toReview = rows.count { it.third }

    CgPage(
        title = stringResource(R.string.cg_title),
        navIcon = R.drawable.ic_cg_home,
        onNav = onHome,
        header = {
            KioskStatusCard(isDeviceOwner, kioskPaused, inSession, toReview, onPauseKiosk, onResumeKiosk, onEndCaregiverSession)
        }
    ) {
        CgLabel(stringResource(R.string.cg_sections))
        rows.forEach { (s, subtitle, warn) ->
            CgSectionRow(s.color, s.icon, stringResource(s.title()), subtitle, warn) { onOpen(s) }
        }
    }
}

/** Tarjeta azul oscuro con el estado del modo quiosco y un botón para pausar o activar. */
@Composable
private fun KioskStatusCard(
    isDeviceOwner: Boolean,
    kioskPaused: Boolean,
    inSession: Boolean,
    toReview: Int,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndSession: () -> Unit
) {
    val (title, subtitle, dot) = when {
        !isDeviceOwner -> Triple(stringResource(R.string.cg_status_off), stringResource(R.string.cg_status_off_sub), Color(0xFFB0BEC5))
        inSession -> Triple(stringResource(R.string.cg_status_session), stringResource(R.string.cg_status_session_sub), Color(0xFFFFD54F))
        kioskPaused -> Triple(stringResource(R.string.cg_status_paused), stringResource(R.string.cg_status_paused_sub), Color(0xFFFFD54F))
        else -> Triple(
            stringResource(R.string.cg_status_active),
            if (toReview == 0) stringResource(R.string.cg_status_ready)
            else stringResource(R.string.cg_status_ready) + " · " + pluralStringResource(R.plurals.cg_status_review, toReview, toReview),
            Color(0xFF69F0AE)
        )
    }
    val action: Pair<String, () -> Unit>? = when {
        !isDeviceOwner -> null
        inSession -> stringResource(R.string.cg_finish) to onEndSession
        kioskPaused -> stringResource(R.string.cg_resume) to onResume
        else -> stringResource(R.string.cg_pause) to onPause
    }
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CgNavy)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(dot.copy(alpha = 0.25f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(dot))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Color(0xFFC5D3E3), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        action?.let { (text, onClick) ->
            Box(
                Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

private fun CgSection.title(): Int = when (this) {
    CgSection.PEOPLE -> R.string.cg_people
    CgSection.SOS -> R.string.cg_sos
    CgSection.HOME -> R.string.settings_home_title
    CgSection.LOCATE -> R.string.cg_locate
    CgSection.HOUSE -> R.string.settings_house_title
    CgSection.EXERCISES -> R.string.settings_exercises_title
    CgSection.RADIO -> R.string.settings_radio_title
    CgSection.WHATSAPP -> R.string.cg_whatsapp
    CgSection.CONNECTION -> R.string.cg_connection
    CgSection.SECURITY -> R.string.cg_security
}

// --- Apartados -----------------------------------------------------------------------

/** Emergencia: a quién llama el SOS y el dibujo del botón. */
@Composable
private fun SosPage(favorites: List<Contact>, noPhoto: Set<Long>, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var sosId by remember { mutableStateOf(settings.sosContactId) }
    var sosIcon by remember { mutableStateOf(settings.sosIcon) }
    CgPage(stringResource(R.string.cg_sos), R.drawable.ic_chevron_left, onBack) {
        CgLabel(stringResource(R.string.settings_sos_title))
        CgCard(padding = 8) {
            if (favorites.isEmpty()) CgHint(stringResource(R.string.settings_no_favorites))
            // Si no se ha elegido, se usa el primer favorito.
            val effective = favorites.firstOrNull { it.id == sosId } ?: favorites.firstOrNull()
            favorites.forEach { contact ->
                PersonOptionRow(contact, contact.id !in noPhoto, onClick = {
                    sosId = contact.id
                    settings.sosContactId = contact.id
                }) {
                    RadioButton(selected = contact == effective, onClick = null)
                }
            }
        }
        SmsCostNote()
        CgLabel(stringResource(R.string.settings_sos_icon_title))
        CgCard {
            CgHint(stringResource(R.string.settings_sos_icon_hint))
            SosIconPicker(sosIcon) {
                sosIcon = it
                settings.sosIcon = it
            }
        }
    }
}

/** Pantalla principal: diseño de los botones y botón para volver. */
@Composable
private fun HomePage(backButton: BackButton, onBackButtonChange: (BackButton) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var homeLayout by remember { mutableStateOf(settings.homeLayout) }
    val sosIcon = remember { settings.sosIcon }
    CgPage(stringResource(R.string.settings_home_title), R.drawable.ic_chevron_left, onBack) {
        CgHint(stringResource(R.string.settings_home_hint))
        HomeLayoutEditor(homeLayout, sosIcon) {
            homeLayout = it
            settings.homeLayout = it
            Kiosk.applyMaps(context)
        }
        CgLabel(stringResource(R.string.settings_back_title))
        CgCard {
            CgHint(stringResource(R.string.settings_back_hint))
            BackButtonPicker(backButton, onBackButtonChange)
        }
    }
}

/** Ubicación por SMS: quién puede pedirla y con qué palabras. */
@Composable
private fun LocatePage(favorites: List<Contact>, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var locateIds by remember { mutableStateOf(settings.locateContactIds) }
    var keywords by remember { mutableStateOf(settings.keywords.joinToString(", ")) }
    CgPage(stringResource(R.string.cg_locate), R.drawable.ic_chevron_left, onBack) {
        CgLabel(stringResource(R.string.settings_locate_title))
        CgCard(padding = 8) {
            if (favorites.isEmpty()) CgHint(stringResource(R.string.settings_no_favorites))
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
        }
        CgLabel(stringResource(R.string.settings_keywords_title))
        CgCard {
            OutlinedTextField(
                value = keywords,
                onValueChange = {
                    keywords = it
                    settings.keywords = it.split(",")
                },
                supportingText = { Text(stringResource(R.string.settings_keywords_hint)) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        SmsCostNote()
    }
}

/** WhatsApp: instalarlo, quién lo tiene, WhatsApp completo y llamadas entrantes. */
@Composable
private fun WhatsAppPage(
    favorites: List<Contact>,
    whatsAppIds: Set<Long>,
    onWhatsAppIds: (Set<Long>) -> Unit,
    onInstallWhatsApp: () -> Unit,
    onAllowOverlay: () -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resumed = rememberOnResume { System.nanoTime() }
    val installed = remember(resumed) { context.isWhatsAppInstalled() }
    val notificationAccess = remember(resumed) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
    val overlayAllowed = remember(resumed) { WhatsAppHomeButton.canShow(context) }
    CgPage(stringResource(R.string.cg_whatsapp), R.drawable.ic_chevron_left, onBack) {
        if (!installed) {
            CgCard {
                CgWarning(stringResource(R.string.cg_wa_missing))
                CgHint(stringResource(R.string.settings_install_whatsapp_hint))
                ActionButton(stringResource(R.string.settings_install_whatsapp), onInstallWhatsApp, color = CgAccent)
            }
        }
        CgLabel(stringResource(R.string.settings_whatsapp_title))
        CgCard(padding = 8) {
            CgHint(stringResource(R.string.settings_whatsapp_hint))
            favorites.forEach { contact ->
                val checked = contact.id in whatsAppIds
                OptionRow(contact.name, contact.phone, onClick = {
                    scope.launch {
                        if (setHasWhatsApp(context, contact, !checked)) {
                            onWhatsAppIds(if (checked) whatsAppIds - contact.id else whatsAppIds + contact.id)
                        }
                    }
                }) {
                    Checkbox(checked = checked, onCheckedChange = null)
                }
            }
        }
        CgLabel(stringResource(R.string.settings_wa_calls_title))
        CgCard {
            if (notificationAccess) {
                CgHint(stringResource(R.string.settings_wa_calls_on))
            } else {
                CgWarning(stringResource(R.string.settings_wa_calls_off))
                ActionButton(stringResource(R.string.settings_wa_calls_enable), onOpenNotificationAccess, color = CgAccent)
            }
        }
        CgLabel(stringResource(R.string.settings_wa_app_title))
        CgCard {
            CgHint(stringResource(R.string.settings_wa_app_hint))
            if (overlayAllowed) {
                CgHint(stringResource(R.string.settings_wa_app_overlay_on))
            } else {
                CgWarning(stringResource(R.string.settings_wa_app_overlay_off))
                ActionButton(stringResource(R.string.settings_wa_app_overlay_allow), onAllowOverlay, color = CgAccent)
            }
        }
    }
}

/** Conexión: Wi-Fi encendido o solo datos móviles. */
@Composable
private fun ConnectionPage(isDeviceOwner: Boolean, onOpenWifiSettings: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { CaregiverSettings(context) }
    var wifiEnabled by remember { mutableStateOf(settings.wifiEnabled) }
    CgPage(stringResource(R.string.cg_connection), R.drawable.ic_chevron_left, onBack) {
        CgLabel(stringResource(R.string.settings_wifi_title))
        CgCard {
            if (isDeviceOwner) {
                SwitchRow(stringResource(R.string.settings_wifi_switch), wifiEnabled) {
                    wifiEnabled = it
                    settings.wifiEnabled = it
                    Kiosk.applyWifi(context)
                }
                CgHint(
                    if (wifiEnabled) stringResource(R.string.settings_wifi_on_hint)
                    else stringResource(R.string.settings_wifi_off_hint)
                )
                if (wifiEnabled) ActionButton(stringResource(R.string.settings_wifi_choose), onOpenWifiSettings)
            } else {
                CgHint(stringResource(R.string.settings_wifi_not_owner))
                ActionButton(stringResource(R.string.settings_wifi_choose), onOpenWifiSettings)
            }
        }
    }
}

/** PIN, modo quiosco, otras apps, quitar el Device Owner y Acerca de. */
@Composable
private fun SecurityPage(
    isDeviceOwner: Boolean,
    kioskPaused: Boolean,
    onChangePin: () -> Unit,
    onOpenContacts: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onPauseKiosk: () -> Unit,
    onResumeKiosk: () -> Unit,
    onDisableKiosk: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var confirmDisable by remember { mutableStateOf(false) }
    CgPage(stringResource(R.string.cg_security), R.drawable.ic_chevron_left, onBack) {
        CgLabel(stringResource(R.string.settings_security_title))
        CgCard { ActionButton(stringResource(R.string.settings_change_pin), onChangePin) }

        if (isDeviceOwner) {
            CgLabel(stringResource(R.string.settings_kiosk_title))
            CgCard {
                if (kioskPaused) {
                    CgHint(stringResource(R.string.settings_kiosk_paused_hint))
                    ActionButton(stringResource(R.string.settings_kiosk_resume), onResumeKiosk, color = VerdeLlamar)
                } else {
                    CgHint(stringResource(R.string.settings_kiosk_active_hint))
                    ActionButton(stringResource(R.string.settings_kiosk_pause), onPauseKiosk)
                }
            }
        }

        CgLabel(stringResource(R.string.settings_other_apps_title))
        CgCard {
            CgHint(
                when {
                    !isDeviceOwner -> stringResource(R.string.settings_not_device_owner)
                    kioskPaused -> stringResource(R.string.settings_kiosk_paused_short)
                    else -> stringResource(R.string.settings_leaves_kiosk)
                }
            )
            ActionButton(stringResource(R.string.settings_open_contacts), onOpenContacts)
            ActionButton(stringResource(R.string.settings_open_android_settings), onOpenAndroidSettings)
        }

        if (isDeviceOwner) {
            CgCard {
                CgHint(stringResource(R.string.settings_remove_owner_hint))
                ActionButton(stringResource(R.string.settings_remove_owner), { confirmDisable = true }, color = RojoSos)
            }
        }

        // Avisos legales que pide la licencia (GNU AGPL v3, sección 5 d).
        CgLabel(stringResource(R.string.settings_about_title))
        CgCard {
            Text("${stringResource(R.string.app_name)} ${appVersion(context)}", color = CgInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            CgHint("Copyright (C) 2026 alvsanmun")
            CgHint(stringResource(R.string.settings_license))
            CgHint(stringResource(R.string.settings_source, SOURCE_URL))
        }
    }

    if (confirmDisable) {
        AlertDialog(
            onDismissRequest = { confirmDisable = false },
            title = { Text(stringResource(R.string.remove_owner_dialog_title)) },
            text = { Text(stringResource(R.string.remove_owner_dialog_text)) },
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

// --- Piezas comunes ------------------------------------------------------------------

@Composable
private fun OptionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        control()
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CgMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = CgInk, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun MoveButton(@DrawableRes icon: Int, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CgNeutral,
            contentColor = CgInk,
            disabledContainerColor = CgNeutral.copy(alpha = 0.5f),
            disabledContentColor = CgChevron
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

/** Botón del panel: gris claro por defecto; con color, el texto va en blanco. */
@Composable
internal fun ActionButton(text: String, onClick: () -> Unit, color: Color = CgNeutral) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = if (color.luminance() > 0.5f) CgInk else Color.White
        ),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** Repositorio con el código fuente (la licencia AGPL obliga a ofrecerlo). */
private const val SOURCE_URL = "https://github.com/alvsanmun/LlamaditApp"

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: PackageManager.NameNotFoundException) {
    ""
}

