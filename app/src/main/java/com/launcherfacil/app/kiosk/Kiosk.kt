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

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.view.Surface
import android.telecom.TelecomManager
import android.util.Log
import com.launcherfacil.app.APP_PERMISSIONS
import com.launcherfacil.app.BACKGROUND_LOCATION
import com.launcherfacil.app.MainActivity
import com.launcherfacil.app.WHATSAPP_PERMISSIONS
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.contacts.WHATSAPP_PACKAGE
import com.launcherfacil.app.gohome.MAPS_PACKAGE

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
        //    Google Maps, para la ruta a pie del botón "Volver a casa" (si está oculto, no se abre).
        val allowed = (listOf(context.packageName, WHATSAPP_PACKAGE, MAPS_PACKAGE) + CALL_PACKAGES + listOfNotNull(dialer))
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

        // 9. Sin modo avión (dejaría el móvil sin llamadas, SMS ni SOS) y Wi-Fi según
        //    elija el cuidador: por defecto apagado y bloqueado (solo la SIM).
        dpm.addUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE)
        applyWifi(context)

        // 10. Apps innecesarias ocultas (no se ejecutan ni gastan batería), salvo con el
        //     quiosco en pausa.
        setHidden(context, !isPaused(context))
        applyMaps(context)

        // 11. Siempre en vertical, también en las pantallas de otras apps (videollamada de
        //     WhatsApp): giro automático apagado y rotación fija a 0.
        lockPortrait(context)
    }

    /**
     * Apaga el giro automático. Necesita el acceso especial "Modificar ajustes del
     * sistema" (WRITE_SETTINGS), que el Device Owner no puede concederse solo:
     *   adb shell appops set com.launcherfacil.app WRITE_SETTINGS allow
     */
    private fun lockPortrait(context: Context) {
        if (!Settings.System.canWrite(context)) {
            Log.w(TAG, "Sin permiso WRITE_SETTINGS: no se puede bloquear la rotación")
            return
        }
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0)
            Settings.System.putInt(context.contentResolver, Settings.System.USER_ROTATION, Surface.ROTATION_0)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo bloquear la rotación", e)
        }
    }

    /** Restricciones que bloquean el Wi-Fi: nadie puede encenderlo ni configurar redes. */
    private val WIFI_RESTRICTIONS = buildList {
        add(UserManager.DISALLOW_CONFIG_WIFI)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(UserManager.DISALLOW_CHANGE_WIFI_STATE)
    }

    /**
     * Aplica el Wi-Fi que ha elegido el cuidador. Apagado (por defecto): bloqueado y el
     * móvil va solo con la SIM. Encendido: se desbloquea para elegir redes en los Ajustes
     * de Android; las redes guardadas se conservan al volver a apagarlo.
     */
    fun applyWifi(context: Context) {
        if (!isDeviceOwner(context)) return
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)
        val enabled = CaregiverSettings(context).wifiEnabled
        if (enabled) {
            for (restriction in WIFI_RESTRICTIONS) dpm.clearUserRestriction(admin, restriction)
            setWifiEnabled(context, true)
        } else {
            setWifiEnabled(context, false)
            for (restriction in WIFI_RESTRICTIONS) dpm.addUserRestriction(admin, restriction)
        }
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

    /** Permisos de Google Maps para guiar la ruta (su diálogo no se vería en el quiosco). */
    private val MAPS_PERMISSIONS = buildList {
        add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        add(android.Manifest.permission.ACCESS_COARSE_LOCATION)
        // La navegación va en una notificación; sin el permiso, Maps lo pregunta y el
        // diálogo no responde dentro del quiosco.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.POST_NOTIFICATIONS)
    }

    /**
     * Google Maps está entre las apps ocultas, salvo que haga falta para "Volver a casa"
     * (el botón se ve o la casa está marcada). Se aplica al cambiar esa configuración.
     */
    fun applyMaps(context: Context) {
        if (!isDeviceOwner(context)) return
        val hidden = !isPaused(context) && !CaregiverSettings(context).needsMaps
        try {
            context.dpm().setApplicationHidden(AdminReceiver.component(context), MAPS_PACKAGE, hidden)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo ${if (hidden) "ocultar" else "mostrar"} Google Maps", e)
        }
        // Los permisos solo se pueden conceder con la app visible (oculta, Android lo rechaza).
        if (!hidden) grant(context, MAPS_PACKAGE, MAPS_PERMISSIONS)
    }

    private fun setHidden(context: Context, hidden: Boolean) {
        val dpm = context.dpm()
        val admin = AdminReceiver.component(context)
        var count = 0
        val keepMaps = CaregiverSettings(context).needsMaps
        for (pkg in HIDDEN_PACKAGES) {
            if (hidden && pkg == MAPS_PACKAGE && keepMaps) continue // Lo usa "Volver a casa".
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
                val granted = DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                var result = dpm.setPermissionGrantState(admin, pkg, permission, granted)
                // Si la política ya decía "concedido" pero Android no lo aplicó (se pidió con
                // la app oculta, como Google Maps), repetirla no hace nada: se quita y se
                // vuelve a poner para que se aplique de verdad.
                if (result && context.packageManager.checkPermission(permission, pkg) != PackageManager.PERMISSION_GRANTED) {
                    dpm.setPermissionGrantState(admin, pkg, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
                    result = dpm.setPermissionGrantState(admin, pkg, permission, granted)
                }
                result
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
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_AIRPLANE_MODE)
        for (restriction in WIFI_RESTRICTIONS) dpm.clearUserRestriction(admin, restriction)
        setHidden(context, false)
        // Obsoleto, pero sigue siendo la única forma de que la app deje de ser Device Owner.
        @Suppress("DEPRECATION")
        dpm.clearDeviceOwnerApp(context.packageName)
        Log.w(TAG, "Modo quiosco desactivado y Device Owner eliminado")
    }

    fun isPaused(context: Context): Boolean = CaregiverSettings(context).kioskPaused

    /**
     * Pausa el quiosco SIN dejar de ser Device Owner: se quita el bloqueo y vuelven a
     * verse las apps ocultas, también tras reiniciar, hasta que se llame a [resume].
     * Se mantienen la app de Teléfono, los permisos, el Wi-Fi elegido, etc.
     */
    fun pause(activity: Activity) {
        CaregiverSettings(activity).kioskPaused = true
        if (isLocked(activity)) activity.stopLockTask()
        if (isDeviceOwner(activity)) setHidden(activity, false)
        Log.i(TAG, "Modo quiosco en pausa")
    }

    /** Vuelve a activar el quiosco tras [pause]. */
    fun resume(activity: Activity) {
        CaregiverSettings(activity).kioskPaused = false
        configure(activity)
        enterLockTask(activity)
        Log.i(TAG, "Modo quiosco activo de nuevo")
    }

    /** Entra en Lock Task si es Device Owner, no está en pausa y aún no está dentro. */
    fun enterLockTask(activity: Activity) {
        if (!isDeviceOwner(activity) || isPaused(activity)) return
        val am = activity.getSystemService(ActivityManager::class.java)
        if (am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        // Recién nombrada Device Owner con la app ya abierta (en Samsung no se puede
        // forzar su cierre para que pase por onCreate): aplicar el quiosco ahora.
        if (!activity.dpm().isLockTaskPermitted(activity.packageName)) configure(activity)
        if (!activity.dpm().isLockTaskPermitted(activity.packageName)) return
        activity.startLockTask()
    }
}
