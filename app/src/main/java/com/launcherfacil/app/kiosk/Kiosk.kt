package com.launcherfacil.app.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
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

    // Ventana de actualizaciones del sistema, en minutos desde medianoche.
    private const val UPDATE_START_MIN = 2 * 60
    private const val UPDATE_END_MIN = 4 * 60

    // Pantallas de llamada que deben poder abrirse dentro del quiosco. Se incluyen
    // las de Google (emulador) y las de Samsung (A16); las que no existan se ignoran.
    private val CALL_PACKAGES = listOf(
        "com.android.server.telecom",
        "com.android.phone",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
    )

    /**
     * Apps del móvil que no se usan: se ocultan (no se ejecutan, no gastan batería ni
     * datos y no se ven), pero no se borran; [disable] las vuelve a mostrar. Lista
     * aprobada por el usuario el 2026-10-06 para el Samsung A16. Se mantienen, entre
     * otras: Contactos, Cámara, Galería, Google Mensajes (la app de SMS), Play Store,
     * Google Play Services, el teléfono de Samsung y "Encontrar mi móvil".
     */
    private val HIDDEN_PACKAGES = listOf(
        // Navegador, mapas, vídeo y fotos de Google.
        "com.android.chrome",
        "com.sec.android.app.chromecustomizations",
        "com.google.android.apps.maps",
        "com.samsung.android.mapsagent",
        "com.samsung.android.tvplus",
        "com.google.android.apps.photos",
        // Facebook preinstalado (servicios en segundo plano).
        "com.facebook.appmanager",
        "com.facebook.services",
        "com.facebook.system",
        // Extras de Samsung.
        "com.samsung.android.calendar",
        "com.samsung.android.app.reminder",
        "com.sec.android.app.clockpackage",
        "com.sec.android.app.myfiles",
        "com.sec.android.app.kidshome",
        "com.samsung.android.app.routines",
        "com.samsung.android.forest",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gos",
        "com.samsung.android.app.sharelive",
        "com.sec.android.easyMover",
        "com.sec.android.easyMover.Agent",
        "com.samsung.android.smartswitchassistant",
        "com.samsung.android.mdx.kit",
        "com.samsung.android.aremoji",
        "com.samsung.android.app.dressroom",
        "com.samsung.android.themestore",
        "com.samsung.android.wallpaper.live",
        "com.android.wallpaper.livepicker",
        "com.samsung.android.stickercenter",
        "com.samsung.android.app.taskedge",
        "com.samsung.android.app.clipboardedge",
        "com.samsung.android.service.peoplestripe",
        "com.samsung.android.dynamiclock",
        "com.samsung.android.samsungpass",
        "com.samsung.android.samsungpassautofill",
        "com.samsung.android.scloud",
        "com.samsung.android.callassistant",
        "com.samsung.android.app.watchmanagerstub",
        "com.samsung.android.beaconmanager",
        // Samsung Mensajes: la app de SMS predeterminada es Google Mensajes.
        "com.samsung.android.messaging",
        // Extras de Google.
        "com.google.android.projection.gearhead",
        "com.google.android.gms.location.history",
        "com.google.android.printservice.recommendation",
        "com.google.android.feedback",
        "com.google.android.apps.accessibility.voiceaccess",
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

        // 5. Ser la app de Teléfono: así las llamadas usan nuestra pantalla (foto grande y
        //    botones enormes). Las notificaciones de llamada del sistema no se verían
        //    dentro del quiosco. Desde Android 14 el Device Owner puede elegirla.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                dpm.setDefaultDialerApplication(context.packageName)
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo poner como app de Teléfono", e)
            }
        }

        // 6. Permisos concedidos sin preguntar (los diálogos no se pueden mostrar en el quiosco).
        grant(context, context.packageName, APP_PERMISSIONS + BACKGROUND_LOCATION)
        grant(context, WHATSAPP_PACKAGE, WHATSAPP_PERMISSIONS)

        // 7. Actualizaciones del sistema automáticas de madrugada (de 2:00 a 4:00):
        //    se instalan y el móvil se reinicia solo, sin preguntar a la persona.
        try {
            dpm.setSystemUpdatePolicy(
                admin, SystemUpdatePolicy.createWindowedInstallPolicy(UPDATE_START_MIN, UPDATE_END_MIN)
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo fijar la política de actualizaciones", e)
        }

        // 8. Bluetooth desactivado: no se usa y gasta batería.
        dpm.addUserRestriction(admin, UserManager.DISALLOW_BLUETOOTH)

        // 9. Solo 4G: Wi-Fi apagado y bloqueado (nadie puede encenderlo ni configurar
        //    redes), y sin modo avión (dejaría el móvil sin llamadas, SMS ni SOS).
        for (restriction in NETWORK_RESTRICTIONS) dpm.addUserRestriction(admin, restriction)
        setWifiEnabled(context, false)

        // 10. Apps innecesarias ocultas (no se ejecutan ni gastan batería).
        setHidden(context, true)
    }

    /** Restricciones de red: sin Wi-Fi (solo 4G) y sin modo avión. */
    private val NETWORK_RESTRICTIONS = buildList {
        add(UserManager.DISALLOW_CONFIG_WIFI)
        add(UserManager.DISALLOW_AIRPLANE_MODE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(UserManager.DISALLOW_CHANGE_WIFI_STATE)
    }

    /** El Device Owner aún puede encender y apagar el Wi-Fi (las apps normales ya no). */
    private fun setWifiEnabled(context: Context, enabled: Boolean) {
        try {
            @Suppress("DEPRECATION")
            context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo ${if (enabled) "encender" else "apagar"} el Wi-Fi", e)
        }
    }

    private fun setHidden(context: Context, hidden: Boolean) {
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)
        var count = 0
        for (pkg in HIDDEN_PACKAGES) {
            try {
                if (dpm.setApplicationHidden(admin, pkg, hidden)) count++
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo ${if (hidden) "ocultar" else "mostrar"} $pkg", e)
            }
        }
        Log.i(TAG, "${if (hidden) "Ocultas" else "Visibles de nuevo"}: $count de ${HIDDEN_PACKAGES.size} apps")
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
        dpm.setSystemUpdatePolicy(admin, null)
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_BLUETOOTH)
        for (restriction in NETWORK_RESTRICTIONS) dpm.clearUserRestriction(admin, restriction)
        setHidden(context, false)
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
        // Recién nombrada Device Owner con la app ya abierta (en Samsung no se puede
        // forzar su cierre para que pase por onCreate): aplicar el quiosco ahora.
        if (!activity.dpm().isLockTaskPermitted(activity.packageName)) configure(activity)
        if (!activity.dpm().isLockTaskPermitted(activity.packageName)) return
        activity.startLockTask()
    }
}
