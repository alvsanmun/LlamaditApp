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
