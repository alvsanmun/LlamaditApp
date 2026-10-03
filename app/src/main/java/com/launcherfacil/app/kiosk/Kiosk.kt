package com.launcherfacil.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import com.launcherfacil.app.APP_PERMISSIONS
import com.launcherfacil.app.BACKGROUND_LOCATION
import com.launcherfacil.app.MainActivity
import com.launcherfacil.app.WHATSAPP_PERMISSIONS
import com.launcherfacil.app.contacts.WHATSAPP_PACKAGE

/**
 * Modo quiosco. Solo funciona si la app es Device Owner; si no, no hace nada y
 * la app se comporta como un launcher normal (útil para desarrollar).
 */
object Kiosk {
    private const val TAG = "Kiosk"

    // Pantallas de llamada que deben poder abrirse dentro del quiosco. Se incluyen
    // las de Google (emulador) y las de Samsung (A16); las que no existan se ignoran.
    private val CALL_PACKAGES = listOf(
        "com.android.server.telecom",
        "com.android.phone",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
    )

    private fun Context.dpm() = getSystemService(DevicePolicyManager::class.java)

    fun isDeviceOwner(context: Context): Boolean =
        context.dpm().isDeviceOwnerApp(context.packageName)

    /** Aplica la configuración del quiosco. Es idempotente: se llama en cada arranque. */
    fun configure(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)

        // 1. Este launcher es siempre la pantalla de inicio (también tras reiniciar).
        val home = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(admin, home, ComponentName(context, MainActivity::class.java))

        // 2. Apps que pueden abrirse dentro del quiosco.
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val allowed = (listOf(context.packageName, WHATSAPP_PACKAGE) + CALL_PACKAGES + listOfNotNull(dialer))
            .distinct()
        dpm.setLockTaskPackages(admin, allowed.toTypedArray())

        // 3. Qué se ve en el quiosco: barra de estado (hora, batería, cobertura) y el
        //    menú de apagado. Sin botón de inicio, recientes ni notificaciones.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            )
        }

        // 4. Sin pantalla de bloqueo: al encender la pantalla se ve el launcher.
        //    (Solo funciona si el móvil no tiene PIN, patrón ni contraseña.)
        dpm.setKeyguardDisabled(admin, true)

        // 5. Permisos concedidos sin preguntar (los diálogos no se pueden mostrar en el quiosco).
        grant(context, context.packageName, APP_PERMISSIONS + BACKGROUND_LOCATION)
        grant(context, WHATSAPP_PACKAGE, WHATSAPP_PERMISSIONS)
    }

    private fun grant(context: Context, pkg: String, permissions: List<String>) {
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)
        for (permission in permissions) {
            val ok = try {
                dpm.setPermissionGrantState(
                    admin, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            } catch (e: Exception) {
                false // Por ejemplo, si WhatsApp no está instalado.
            }
            if (!ok) Log.w(TAG, "No se pudo conceder $permission a $pkg")
        }
    }

    fun isLocked(context: Context): Boolean =
        context.getSystemService(ActivityManager::class.java).lockTaskModeState !=
            ActivityManager.LOCK_TASK_MODE_NONE

    /**
     * Para el cuidador: sale del Lock Task y abre otra app (Contactos, Ajustes...).
     * Al volver al launcher, [enterLockTask] lo vuelve a bloquear.
     */
    fun pauseAndOpen(activity: Activity, intent: Intent): Boolean {
        if (isLocked(activity)) activity.stopLockTask()
        return try {
            activity.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No se pudo abrir $intent", e)
            false
        }
    }

    /**
     * Desactiva el modo quiosco para siempre: sale del Lock Task, quita la pantalla de
     * inicio fija y deja de ser Device Owner. Para volver a activarlo hay que repetir
     * `dpm set-device-owner` (y el móvil no debe tener cuentas).
     */
    fun disable(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)
        if (context is Activity && isLocked(context)) context.stopLockTask()
        // Vaciar la lista de Lock Task también saca del modo quiosco.
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.setKeyguardDisabled(admin, false)
        // Obsoleto, pero sigue siendo la única forma de que la app deje de ser Device Owner.
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.w(TAG, "Modo quiosco desactivado y Device Owner eliminado")
    }

    /** Entra en Lock Task si es Device Owner y aún no está dentro. */
    fun enterLockTask(activity: Activity) {
        if (!isDeviceOwner(activity)) return
        val am = activity.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        if (!activity.dpm().isLockTaskPermitted(activity.packageName)) return
        activity.startLockTask()
    }
}
