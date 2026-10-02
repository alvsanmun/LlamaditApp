package com.launcherfacil.app.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context

/**
 * Administrador del dispositivo. Se convierte en Device Owner con:
 *   adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }
}
