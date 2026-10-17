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

package com.launcherfacil.app.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * SOLO EN DEBUG: salida de emergencia del quiosco para desarrollar, por si no se
 * puede entrar en la configuración del cuidador (por ejemplo, PIN olvidado):
 *
 *   adb shell am broadcast -a com.launcherfacil.app.DEBUG_EXIT_KIOSK -p com.launcherfacil.app
 *
 * Protegido con el permiso DUMP: lo tiene adb (shell), pero no las apps normales.
 */
class DebugExitKioskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Kiosk.disable(context)
    }
}
