package com.launcherfacil.app.whatsapp

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Videollamada (o llamada) entrante de WhatsApp, leída de su notificación. */
data class WhatsAppCall(
    val key: String,
    val name: String,
    val isVideo: Boolean,
    /** Foto que trae la notificación (la de perfil de WhatsApp), si la hay. */
    val notificationPhoto: Bitmap?,
    val answer: PendingIntent,
    val decline: PendingIntent?
)

/** Llamada de WhatsApp sonando ahora mismo (o null). */
object WhatsAppIncoming {
    private const val TAG = "WhatsAppIncoming"

    private val _call = MutableStateFlow<WhatsAppCall?>(null)
    val call: StateFlow<WhatsAppCall?> = _call

    /** Último momento en que se contestó o rechazó desde nuestra pantalla. */
    private var handledAt = 0L

    // Tras contestar, WhatsApp vuelve a publicar un instante la notificación de "entrante"
    // (visto en el A16 el 2026-10-06): durante este tiempo se ignora.
    private const val IGNORE_AFTER_HANDLED_MS = 5_000L

    /** Muestra la llamada; devuelve false si se ignora (acaba de contestarse o rechazarse). */
    fun show(call: WhatsAppCall): Boolean {
        if (System.currentTimeMillis() - handledAt < IGNORE_AFTER_HANDLED_MS) return false
        _call.value = call
        return true
    }

    fun clear(key: String? = null) {
        if (key == null || _call.value?.key == key) _call.value = null
    }

    fun answer(context: Context) = send(context, _call.value?.answer)

    fun decline(context: Context) = send(context, _call.value?.decline)

    /**
     * Pulsa por dentro el botón de la notificación de WhatsApp. Desde Android 14
     * hay que permitir expresamente que ese PendingIntent abra la pantalla de WhatsApp.
     */
    private fun send(context: Context, intent: PendingIntent?) {
        if (intent == null) return
        handledAt = System.currentTimeMillis()
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                ).toBundle()
        } else null
        try {
            intent.send(context, 0, null, null, null, null, options)
        } catch (e: PendingIntent.CanceledException) {
            Log.w(TAG, "La llamada de WhatsApp ya no está disponible", e)
        }
        clear()
    }
}

// Claves de Notification.CallStyle (Android 12+). Se escriben a mano para poder
// leerlas aunque la constante no exista en versiones anteriores.
private const val EXTRA_CALL_TYPE = "android.callType"
private const val EXTRA_CALL_IS_VIDEO = "android.callIsVideo"
private const val EXTRA_CALL_PERSON = "android.callPerson"
private const val EXTRA_ANSWER_INTENT = "android.answerIntent"
private const val EXTRA_DECLINE_INTENT = "android.declineIntent"
private const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"
private const val CALL_TYPE_INCOMING = 1
private const val CALL_TYPE_ONGOING = 2

// Si WhatsApp no usa CallStyle, se buscan los botones por su texto (varios idiomas).
private val ANSWER_WORDS = Regex("contestar|responder|aceptar|atender|answer|accept|respond", RegexOption.IGNORE_CASE)
private val DECLINE_WORDS = Regex("rechazar|colgar|ignorar|decline|reject|dismiss|hang ?up", RegexOption.IGNORE_CASE)
private val VIDEO_WORDS = Regex("v[ií]deo", RegexOption.IGNORE_CASE)

/**
 * Interpreta una notificación de WhatsApp. Devuelve la llamada si es una llamada
 * ENTRANTE con botón de contestar; null en cualquier otro caso (mensajes, llamada
 * en curso, perdida...).
 */
fun parseWhatsAppCall(context: Context, sbn: StatusBarNotification): WhatsAppCall? {
    val n = sbn.notification
    val extras = n.extras ?: Bundle.EMPTY

    val callType = extras.getInt(EXTRA_CALL_TYPE, 0)
    if (callType != 0 && callType != CALL_TYPE_INCOMING) return null // en curso, filtrado...

    val actions = n.actions.orEmpty()
    val answer = extras.parcelable<PendingIntent>(EXTRA_ANSWER_INTENT)
        ?: actions.firstOrNull { ANSWER_WORDS.containsMatchIn(it.title ?: "") }?.actionIntent
        ?: return null
    val decline = extras.parcelable<PendingIntent>(EXTRA_DECLINE_INTENT)
        ?: actions.firstOrNull { DECLINE_WORDS.containsMatchIn(it.title ?: "") }?.actionIntent

    val person = extras.parcelable<android.app.Person>(EXTRA_CALL_PERSON)
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
    val name = person?.name?.toString() ?: title.orEmpty()

    val isVideo = if (extras.containsKey(EXTRA_CALL_IS_VIDEO)) {
        extras.getBoolean(EXTRA_CALL_IS_VIDEO)
    } else {
        VIDEO_WORDS.containsMatchIn(text) || VIDEO_WORDS.containsMatchIn(title.orEmpty())
    }

    val icon = person?.icon ?: n.getLargeIcon()
    return WhatsAppCall(sbn.key, name, isVideo, icon?.toBitmap(context), answer, decline)
}

/** ¿Es la notificación de una llamada de WhatsApp en curso ("Videollamada en curso")? */
fun isOngoingCall(sbn: StatusBarNotification): Boolean {
    val extras = sbn.notification.extras ?: return false
    return extras.getInt(EXTRA_CALL_TYPE, 0) == CALL_TYPE_ONGOING ||
        extras.parcelable<PendingIntent>(EXTRA_HANG_UP_INTENT) != null
}

/** Vuelca la notificación al log: sirve para ajustar el reconocimiento en el A16. */
@Suppress("DEPRECATION") // Bundle.get: solo para mostrar los valores en el log.
fun logWhatsAppNotification(sbn: StatusBarNotification) {
    val n = sbn.notification
    val extras = n.extras
    val keys = extras?.keySet()?.joinToString { k -> "$k=${extras.get(k)}" }
    val actions = n.actions.orEmpty().joinToString { it.title.toString() }
    Log.i(
        "WhatsAppNotif",
        "key=${sbn.key} category=${n.category} ongoing=${sbn.isOngoing} " +
            "fullScreen=${n.fullScreenIntent != null} actions=[$actions] extras={$keys}"
    )
}

private fun Icon.toBitmap(context: Context): Bitmap? = try {
    loadDrawable(context)?.let { d ->
        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 256
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 256
        createBitmap(w, h).also { b ->
            d.setBounds(0, 0, w, h)
            d.draw(Canvas(b))
        }
    }
} catch (e: Exception) {
    null
}

@Suppress("DEPRECATION")
private inline fun <reified T> Bundle.parcelable(key: String): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelable(key, T::class.java)
    else getParcelable(key) as? T
