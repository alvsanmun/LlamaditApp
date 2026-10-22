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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R

/**
 * Interruptor de una función que envía SMS. Al activarla avisa de que, según la tarifa
 * de la línea, los SMS pueden cobrarse, y solo se activa si el cuidador lo confirma.
 * Desactivarla no pide nada.
 */
@Composable
fun SmsSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    var confirming by remember { mutableStateOf(false) }
    SwitchRow(title, checked) { on ->
        if (on) confirming = true else onChange(false)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(R.string.sms_cost_title)) },
            text = { Text(stringResource(R.string.sms_cost_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    onChange(true)
                }) { Text(stringResource(R.string.sms_cost_accept)) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

/** Recordatorio fijo en las secciones que envían SMS (SOS, ubicación, volver a casa). */
@Composable
fun SmsCostNote() {
    Text("⚠ " + stringResource(R.string.sms_cost_note), color = BateriaMedia, fontSize = 15.sp)
}
