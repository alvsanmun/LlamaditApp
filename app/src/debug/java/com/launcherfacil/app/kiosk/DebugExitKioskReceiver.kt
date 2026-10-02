package com.launcherfacil.app.kiosk

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SOLO EN DEBUG: salida de emergencia del quiosco para desarrollar.
 * Un Device Owner normal no se puede quitar por adb (solo con un restablecimiento
 * de fábrica), así que esto lo quita desde la propia app:
 *
 *   adb shell am broadcast -a com.launcherfacil.app.DEBUG_EXIT_KIOSK -p com.launcherfacil.app
 *
 * Protegido con el permiso DUMP: lo tiene adb (shell), pero no las apps normales.
 */
class DebugExitKioskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        if (!dpm.isDeviceOwnerApp(context.packageName)) return
        val admin = AdminReceiver.component(context)
        // Vaciar la lista de Lock Task saca a la app del modo quiosco.
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.setKeyguardDisabled(admin, false)
        // Obsoleto, pero sigue siendo la única forma de que la app deje de ser Device Owner.
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.w("Kiosk", "Modo quiosco desactivado y Device Owner eliminado (debug)")
    }
}
