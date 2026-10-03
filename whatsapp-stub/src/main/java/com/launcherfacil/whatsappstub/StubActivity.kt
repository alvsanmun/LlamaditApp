package com.launcherfacil.whatsappstub

import android.app.Activity
import android.app.NotificationManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView

/**
 * Muestra (y registra en logcat) qué ha pedido el launcher:
 * - una llamada saliente (fila de la agenda con el mimetype de vídeo o de voz);
 * - "contestar" una llamada simulada (extra "answered");
 * - la pantalla de llamada entrante propia de WhatsApp (extra "ringing").
 */
class StubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val video = intent.getBooleanExtra("video", intent.type?.contains("video") == true)
        val kind = if (video) "VIDEOLLAMADA" else "LLAMADA DE VOZ"
        val text = when {
            intent.getBooleanExtra("answered", false) -> {
                getSystemService(NotificationManager::class.java).cancel(CALL_NOTIFICATION_ID)
                "WhatsApp falso\n\n$kind EN CURSO\n(contestada)"
            }
            intent.getBooleanExtra("ringing", false) -> "WhatsApp falso\n\nPantalla propia de\n$kind ENTRANTE"
            else -> "WhatsApp falso\n\n$kind\n\n${intent.data}"
        }
        Log.i("WhatsAppStub", text.replace("\n", " ") + " ${intent.type ?: ""}")
        setContentView(TextView(this).apply {
            this.text = text
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#075E54"))
        })
    }
}
