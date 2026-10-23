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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.config.ButtonSize
import com.launcherfacil.app.config.DEFAULT_HOME_LAYOUT
import com.launcherfacil.app.config.HomeEntry
import com.launcherfacil.app.config.HomeItem
import com.launcherfacil.app.config.SosIcon
import com.launcherfacil.app.config.toRows

@Composable
fun HomeScreen(
    onCall: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    onDialpad: () -> Unit = {},
    onSos: () -> Unit = {},
    onCaregiver: () -> Unit = {},
    onMessages: () -> Unit = {},
    onMissedCalls: () -> Unit = {},
    onGoHome: () -> Unit = {},
    onWhatsAppApp: () -> Unit = {},
    onExercises: () -> Unit = {},
    whatsAppUnread: Int = 0,
    newSms: Int = 0,
    newMissedCalls: Int = 0,
    layout: List<HomeEntry> = DEFAULT_HOME_LAYOUT,
    sosIcon: SosIcon = SosIcon.TEXT
) {
    val rows = layout.toRows()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        rows.forEach { row ->
            when (row.entries.first().item) {
                // Acceso oculto del cuidador: mantener pulsado el reloj 5 segundos. Por
                // eso el reloj siempre se ve (el cuidador solo puede moverlo).
                HomeItem.CLOCK -> BigClock(Modifier.holdFor(CAREGIVER_HOLD_MS, onCaregiver))
                HomeItem.BATTERY -> BigBattery()
                else -> Row(
                    // Los botones medianos y grandes se reparten el alto libre; los
                    // pequeños son bajos, para dejar más sitio a los demás.
                    modifier = Modifier.fillMaxWidth()
                        .then(if (row.stretches) Modifier.weight(1f) else Modifier.height(SMALL_ROW_HEIGHT)),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    row.entries.forEach { entry ->
                        val modifier = Modifier.weight(1f).fillMaxHeight()
                        val fraction = if (entry.size == ButtonSize.SMALL) 0.65f else 0.55f
                        when (entry.item) {
                            HomeItem.CALL -> BigTile(VerdeLlamar, onCall, modifier) {
                                TileIcon(R.drawable.ic_call, fraction)
                            }
                            HomeItem.WHATSAPP -> BigTile(VerdeWhatsApp, onWhatsApp, modifier) {
                                TileIcon(R.drawable.ic_whatsapp_video, fraction)
                            }
                            HomeItem.DIALPAD -> BigTile(AzulTeclado, onDialpad, modifier) {
                                TileIcon(R.drawable.ic_dialpad, fraction)
                            }
                            HomeItem.SOS -> SosButton(onSos, modifier, sosIcon)
                            HomeItem.MESSAGES ->
                                SmallTileWithBadge(NaranjaMensajes, R.drawable.ic_sms, newSms, onMessages, modifier, fraction)
                            HomeItem.MISSED_CALLS ->
                                SmallTileWithBadge(MoradoPerdidas, R.drawable.ic_phone_missed, newMissedCalls, onMissedCalls, modifier, fraction)
                            HomeItem.WHATSAPP_APP ->
                                SmallTileWithBadge(CianWhatsApp, R.drawable.ic_chat, whatsAppUnread, onWhatsAppApp, modifier, fraction)
                            HomeItem.EXERCISES -> BigTile(RosaEjercicios, onExercises, modifier) {
                                TileIcon(R.drawable.ic_extension, fraction)
                            }
                            HomeItem.GO_HOME -> BigTile(MarronCasa, onGoHome, modifier) {
                                TileIcon(R.drawable.ic_go_home, fraction)
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }
        // Sin botones que se estiren, lo que sobra queda abajo.
        if (rows.none { it.stretches }) Spacer(Modifier.weight(1f))
    }
}

private const val CAREGIVER_HOLD_MS = 5000L
private val SMALL_ROW_HEIGHT = 76.dp

@Composable
private fun SmallTileWithBadge(
    color: Color,
    icon: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fraction: Float = 0.65f
) {
    Box(modifier.fillMaxHeight()) {
        BigTile(color, onClick, Modifier.fillMaxSize()) { TileIcon(icon, fraction) }
        if (count > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(40.dp)
                    .background(BateriaBaja, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (count > 99) "99+" else count.toString(),
                    color = TextoClaro,
                    fontSize = if (count > 9) 17.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Llama a [onTrigger] si se mantiene pulsado [millis] sin soltar. Sin respuesta visual. */
private fun Modifier.holdFor(millis: Long, onTrigger: () -> Unit): Modifier = composed {
    val currentOnTrigger by rememberUpdatedState(onTrigger)
    val haptics = LocalHapticFeedback.current
    pointerInput(Unit) {
        coroutineScope {
            detectTapGestures(onPress = {
                val hold = launch {
                    delay(millis)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentOnTrigger()
                }
                tryAwaitRelease()
                hold.cancel()
            })
        }
    }
}

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
