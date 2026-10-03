package com.launcherfacil.app.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.launcherfacil.app.MainActivity

/**
 * Administrador del dispositivo. Se convierte en Device Owner con:
 *   adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)
    }

    /** Recién nombrado Device Owner: aplica el quiosco y abre el launcher, que se bloquea. */
    override fun onEnabled(context: Context, intent: Intent) {
        Kiosk.configure(context)
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
