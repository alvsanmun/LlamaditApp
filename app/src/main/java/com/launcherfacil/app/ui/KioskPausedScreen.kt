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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R

/**
 * Lo que se ve al abrir LlamaditApp con el quiosco en pausa. Activarlo no pide el PIN:
 * solo deja el móvil más protegido, y así es de un toque. El panel sí lo pide.
 */
@Composable
fun KioskPausedScreen(onActivate: () -> Unit, onOpenPanel: () -> Unit, onUsePhone: () -> Unit) {
    CaregiverTheme {
        LightStatusBar()
        Column(
            Modifier.fillMaxSize().background(CgBg).safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
        ) {
            Text(stringResource(R.string.cg_status_paused), color = CgInk, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            CgHint(stringResource(R.string.kiosk_paused_text))
            ActionButton(stringResource(R.string.settings_kiosk_resume), onActivate, color = VerdeLlamar)
            ActionButton(stringResource(R.string.kiosk_open_panel), onOpenPanel)
            ActionButton(stringResource(R.string.kiosk_use_phone), onUsePhone)
        }
    }
}
