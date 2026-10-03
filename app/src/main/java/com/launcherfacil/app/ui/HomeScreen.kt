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

@Composable
fun HomeScreen(
    onCall: () -> Unit = {},
    onWhatsApp: () -> Unit = {},
    onDialpad: () -> Unit = {},
    onSos: () -> Unit = {},
    onCaregiver: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        // Acceso oculto del cuidador: mantener pulsado el reloj 5 segundos.
        BigClock(Modifier.holdFor(CAREGIVER_HOLD_MS, onCaregiver))
        Spacer(Modifier.height(16.dp))
        BigBattery()
        Spacer(Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BigTile(VerdeLlamar, onCall, Modifier.weight(1f).fillMaxHeight()) {
                    TileIcon(R.drawable.ic_call)
                }
                BigTile(VerdeWhatsApp, onWhatsApp, Modifier.weight(1f).fillMaxHeight()) {
                    TileIcon(R.drawable.ic_whatsapp_video)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BigTile(AzulTeclado, onDialpad, Modifier.weight(1f).fillMaxHeight()) {
                    TileIcon(R.drawable.ic_dialpad)
                }
                SosButton(onSos, Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

private const val CAREGIVER_HOLD_MS = 5000L

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
