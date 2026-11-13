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

package com.launcherfacil.app.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.edit
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.launcherfacil.app.config.CaregiverSettings
import com.launcherfacil.app.ui.toBatteryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Gestión a distancia (2026-11-02): el cuidador maneja el panel desde su móvil con la
 * web del cuidador. Va por internet con Firebase (Firestore + acceso anónimo).
 *
 * **Cada familia usa su propio proyecto de Firebase** (decisión del 2026-11-02: el autor
 * no quiere guardar los datos de nadie). La app no lleva ningún proyecto dentro: el
 * cuidador lo crea con la guía (docs/GESTION-A-DISTANCIA.md), pega sus datos en la web
 * y la web le enseña un QR con esos datos y una invitación; aquí se le hace una foto
 * ([linkFromQr]) y el móvil se conecta a ese proyecto.
 *
 * - `devices/{uid}`: este móvil (uid anónimo). Guarda la lista de cuidadores vinculados
 *   y una copia del estado (personas, pantalla, ejercicios…) que lee la web.
 * - `devices/{uid}/commands`: órdenes de los cuidadores (pedir la ubicación, poner el
 *   SOS, añadir una persona…). Se ejecutan aquí y se marca el resultado.
 * - `devices/{uid}/photos/{contactId}`: fotos pequeñas de las personas para la web.
 * - `devices/{uid}/events`: lo que pasa en el móvil (SOS, Volver a casa, batería baja).
 * - `invites/{token}`: invitación de un cuidador (la crea su web; 15 min, un uso).
 *
 * Las reglas de seguridad están en `firebase/firestore.rules`.
 */
object Remote {
    private const val TAG = "Remote"
    private const val PREFS = "remote"
    private const val KEY_NAME = "name"
    private const val KEY_PROJECT = "projectId"
    private const val KEY_API_KEY = "apiKey"
    private const val KEY_APP_ID = "appId"
    private const val FIREBASE_APP = "family"
    private const val QR_PREFIX = "LLAMADITAPP:"
    private const val HEARTBEAT_MS = 30 * 60_000L

    enum class Status { OFF, CONNECTING, ONLINE, ERROR }

    data class Caregiver(val uid: String, val name: String)

    /** Datos públicos del proyecto de Firebase de la familia (los de su app web). */
    data class Project(val projectId: String, val apiKey: String, val appId: String)

    /** Lo que trae el QR de la web del cuidador. */
    data class Invite(val project: Project, val token: String)

    sealed interface LinkResult {
        data class Linked(val name: String) : LinkResult
        /** El QR es de otro proyecto: hay que confirmar el cambio (`switchProject`). */
        data class OtherProject(val projectId: String) : LinkResult
        data object Invalid : LinkResult
        data object Expired : LinkResult
        data object Failed : LinkResult
    }

    private val _status = MutableStateFlow(Status.OFF)
    val status: StateFlow<Status> = _status
    private val _caregivers = MutableStateFlow(emptyList<Caregiver>())
    val caregivers: StateFlow<List<Caregiver>> = _caregivers

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null
    private var publishJob: Job? = null
    private val listeners = mutableListOf<ListenerRegistration>()
    private var batteryReceiver: BroadcastReceiver? = null
    private var contactsObserver: ContentObserver? = null
    private var lastBattery = -1 to false
    internal var deviceId: String? = null
        private set

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun project(context: Context): Project? {
        val p = prefs(context)
        val id = p.getString(KEY_PROJECT, null) ?: return null
        return Project(id, p.getString(KEY_API_KEY, "").orEmpty(), p.getString(KEY_APP_ID, "").orEmpty())
    }

    /** Conectado a un proyecto de Firebase de la familia. */
    fun isEnabled(context: Context) = project(context) != null

    /** Nombre de la persona, para que el cuidador la reconozca en su lista. */
    fun deviceName(context: Context): String = prefs(context).getString(KEY_NAME, "").orEmpty()

    fun setDeviceName(context: Context, name: String) {
        prefs(context).edit { putString(KEY_NAME, name.trim()) }
        publishSoon(context)
    }

    /** La conexión con el proyecto de la familia (se crea la primera vez). */
    private fun firebase(context: Context): FirebaseApp? {
        val project = project(context) ?: return null
        FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP }?.let { existing ->
            if (existing.options.projectId == project.projectId && existing.options.apiKey == project.apiKey) return existing
            existing.delete()
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(project.projectId)
            .setApiKey(project.apiKey)
            .setApplicationId(project.appId)
            .build()
        return FirebaseApp.initializeApp(context.applicationContext, options, FIREBASE_APP)
    }

    internal fun db(): FirebaseFirestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(FIREBASE_APP))

    /** Se llama al arrancar el launcher: conecta si hay un proyecto de la familia. */
    fun start(context: Context) {
        val app = context.applicationContext
        if (job?.isActive == true) return
        val firebase = firebase(app) ?: return
        _status.value = Status.CONNECTING
        job = scope.launch {
            try {
                val auth = FirebaseAuth.getInstance(firebase)
                val uid = auth.currentUser?.uid ?: auth.signInAnonymously().await().user!!.uid
                deviceId = uid
                val db = FirebaseFirestore.getInstance(firebase)
                db.collection("devices").document(uid).set(mapOf("platform" to "android"), SetOptions.merge()).await()
                listen(app, db, uid)
                watchLocalChanges(app)
                publish(app)
                _status.value = Status.ONLINE
                while (isActive) {
                    delay(HEARTBEAT_MS)
                    publish(app)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "No se pudo conectar", e)
                _status.value = Status.ERROR
                // Se reintenta más tarde (sin datos móviles, por ejemplo).
                job = null
                scope.launch {
                    delay(60_000)
                    if (_status.value == Status.ERROR) start(app)
                }
            }
        }
    }

    /** Desconecta. Con [unlinkAll] además olvida a todos los cuidadores. */
    private suspend fun stop(context: Context, unlinkAll: Boolean) {
        val id = deviceId
        if (unlinkAll && id != null) {
            withTimeoutOrNull(10_000) {
                runCatching {
                    db().collection("devices").document(id)
                        .update(mapOf("caregivers" to emptyList<String>(), "caregiverNames" to emptyMap<String, String>())).await()
                }
            }
        }
        listeners.forEach { it.remove() }
        listeners.clear()
        job?.cancel()
        job = null
        publishJob?.cancel()
        batteryReceiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        batteryReceiver = null
        contactsObserver?.let { context.applicationContext.contentResolver.unregisterContentObserver(it) }
        contactsObserver = null
        deviceId = null
        _status.value = Status.OFF
        _caregivers.value = emptyList()
    }

    /** Deja el proyecto de la familia: los cuidadores dejan de ver este móvil. */
    suspend fun disconnect(context: Context) {
        stop(context, unlinkAll = true)
        FirebaseApp.getApps(context).firstOrNull { it.name == FIREBASE_APP }?.let { app ->
            runCatching { FirebaseAuth.getInstance(app).signOut() }
            app.delete()
        }
        prefs(context).edit { remove(KEY_PROJECT); remove(KEY_API_KEY); remove(KEY_APP_ID) }
    }

    /** Lee el texto del QR de la web del cuidador (null si no es de LlamaditApp). */
    fun parseInvite(text: String): Invite? = runCatching {
        if (!text.startsWith(QR_PREFIX)) return null
        val json = JSONObject(text.removePrefix(QR_PREFIX))
        val project = Project(json.getString("p"), json.getString("k"), json.getString("a"))
        val token = json.getString("i")
        if (project.projectId.isBlank() || project.apiKey.isBlank() || token.length < 20) null
        else Invite(project, token)
    }.getOrNull()

    /**
     * Vincula al cuidador del QR. Si el móvil aún no está conectado a ningún proyecto, se
     * conecta al del QR; si está conectado a otro, devuelve [LinkResult.OtherProject] salvo
     * que se pida [switchProject] (los cuidadores de antes dejan de verlo).
     */
    suspend fun linkFromQr(context: Context, text: String, switchProject: Boolean = false): LinkResult {
        val app = context.applicationContext
        val invite = parseInvite(text) ?: return LinkResult.Invalid
        var current = project(app)
        if (current != null && current.projectId != invite.project.projectId) {
            if (!switchProject) return LinkResult.OtherProject(invite.project.projectId)
            disconnect(app)
            current = null
        }
        if (current != invite.project) {
            // Mismo proyecto con otra clave (la han cambiado): se vuelve a conectar.
            if (current != null) stop(app, unlinkAll = false)
            prefs(app).edit {
                putString(KEY_PROJECT, invite.project.projectId)
                putString(KEY_API_KEY, invite.project.apiKey)
                putString(KEY_APP_ID, invite.project.appId)
            }
        }
        start(app)
        // Se espera a estar conectado (o a que falle) antes de aceptar la invitación.
        val status = withTimeoutOrNull(30_000) { _status.first { it == Status.ONLINE || it == Status.ERROR } }
        val id = deviceId
        if (status != Status.ONLINE || id == null) {
            // Si nunca había estado conectado, no se guarda el proyecto: los datos podían
            // estar mal o el proyecto sin preparar.
            if (current == null) disconnect(app)
            return LinkResult.Failed
        }
        return try {
            val db = db()
            val inviteRef = db.collection("invites").document(invite.token)
            val data = inviteRef.get().await()
            val caregiver = data.getString("caregiver")
            val expires = data.getTimestamp("expires")?.toDate()?.time ?: 0L
            if (!data.exists() || caregiver == null || expires < System.currentTimeMillis()) return LinkResult.Expired
            val name = data.getString("name").orEmpty().take(40)
            db.collection("devices").document(id).update(
                mapOf(
                    "caregivers" to FieldValue.arrayUnion(caregiver),
                    "caregiverNames.$caregiver" to name
                )
            ).await()
            // Una invitación, un móvil. La web ve aparecer este móvil en su lista.
            inviteRef.delete()
            addEvent(app, "linked", mapOf("name" to name))
            publish(app)
            LinkResult.Linked(name)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo aceptar la invitación", e)
            LinkResult.Failed
        }
    }

    private fun listen(context: Context, db: FirebaseFirestore, uid: String) {
        listeners.forEach { it.remove() }
        listeners.clear()
        val device = db.collection("devices").document(uid)
        // Cuidadores vinculados.
        listeners += device.addSnapshotListener { snap, _ ->
            val ids = (snap?.get("caregivers") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val names = (snap?.get("caregiverNames") as? Map<*, *>).orEmpty()
            _caregivers.value = ids.map { Caregiver(it, names[it] as? String ?: "") }
        }
        // Órdenes pendientes, de la más antigua a la más nueva (se ordenan aquí: ordenar
        // en la consulta obligaría a crear un índice en Firebase).
        listeners += device.collection("commands")
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snap, e ->
                if (e != null) Log.w(TAG, "Error al escuchar las órdenes", e)
                snap?.documentChanges
                    ?.filter { it.type == DocumentChange.Type.ADDED && !it.document.metadata.hasPendingWrites() }
                    ?.map { it.document }
                    ?.sortedBy { it.getTimestamp("at")?.seconds ?: 0L }
                    ?.forEach { doc -> scope.launch { RemoteCommands.run(context, doc) } }
            }
    }

    suspend fun removeCaregiver(uid: String): Boolean {
        val id = deviceId ?: return false
        return try {
            db().collection("devices").document(id).update(
                mapOf("caregivers" to FieldValue.arrayRemove(uid), "caregiverNames.$uid" to FieldValue.delete())
            ).await()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Publica el estado dentro de un momento (agrupa varios cambios seguidos). */
    fun publishSoon(context: Context) {
        if (job?.isActive != true) return
        publishJob?.cancel()
        publishJob = scope.launch {
            delay(2_000)
            // Solo se cancela la espera: una publicación empezada termina (si no, con cambios
            // seguidos se cortaba a mitad y la web se quedaba con el estado viejo).
            withContext(NonCancellable) { publish(context.applicationContext) }
        }
    }

    private suspend fun publish(context: Context) {
        val id = deviceId ?: return
        try {
            RemoteState.publish(context, db().collection("devices").document(id), deviceName(context))
            Log.i(TAG, "Estado publicado")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo publicar el estado", e)
        }
    }

    /** Apunta algo que ha pasado en el móvil para que el cuidador lo vea en su web. */
    fun addEvent(context: Context, type: String, extra: Map<String, Any?> = emptyMap()) {
        val id = deviceId ?: return
        if (!isEnabled(context)) return
        db().collection("devices").document(id).collection("events")
            .add(mapOf("type" to type, "at" to FieldValue.serverTimestamp()) + extra)
    }

    /** Batería y agenda: si cambian, se vuelve a publicar el estado. */
    private fun watchLocalChanges(context: Context) {
        if (batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    val battery = intent.toBatteryState()
                    val (percent, charging) = lastBattery
                    if (percent < 0 || charging != battery.charging || kotlin.math.abs(percent - battery.percent) >= 5) {
                        val limit = CaregiverSettings(c).lowBatteryLimit
                        if (battery.percent <= limit && !battery.charging && (percent > limit || percent < 0)) {
                            addEvent(c, "lowBattery", mapOf("percent" to battery.percent))
                        }
                        lastBattery = battery.percent to battery.charging
                        publishSoon(c)
                    }
                }
            }
            context.registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        if (contactsObserver == null) {
            contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = publishSoon(context)
            }
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, contactsObserver!!)
        }
    }
}
