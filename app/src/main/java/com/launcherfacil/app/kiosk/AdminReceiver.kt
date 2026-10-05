package com.launcherfacil.app.kiosk

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.launcherfacil.app.MainActivity

/**
 * Administrador del dispositivo. Se convierte en Device Owner con:
 *   adb shell dpm set-device-owner com.launcherfacil.app/.kiosk.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {
    companion object {
        fun component(context: Context) = ComponentName(context, AdminReceiver::class.java)

        private const val POLL_MS = 300L
        private const val MAX_WAIT_MS = 9_000L
    }

    /**
     * Recién nombrado administrador: aplica el quiosco y abre el launcher, que se
     * bloquea. En Samsung este aviso llega un instante ANTES de ser Device Owner,
     * así que se espera (unos segundos como mucho) a que lo sea.
     */
    override fun onEnabled(context: Context, intent: Intent) {
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var waited = 0L
        fun check() {
            if (Kiosk.isDeviceOwner(context)) {
                Kiosk.configure(context)
                context.startActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                pending.finish()
            } else if (waited >= MAX_WAIT_MS) {
                Log.w("Kiosk", "Administrador activado, pero la app no es Device Owner")
                pending.finish()
            } else {
                waited += POLL_MS
                handler.postDelayed(::check, POLL_MS)
            }
        }
        check()
    }
}
