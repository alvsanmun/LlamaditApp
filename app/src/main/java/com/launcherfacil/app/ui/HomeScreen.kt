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
import androidx.compose.runtime.Composable
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
    onSos: () -> Unit = {}
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
        BigClock()
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
                    TileIcon(R.drawable.ic_whatsapp_call)
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

@Preview(widthDp = 411, heightDp = 891)
@Composable
private fun HomeScreenPreview() {
    HomeScreen()
}
