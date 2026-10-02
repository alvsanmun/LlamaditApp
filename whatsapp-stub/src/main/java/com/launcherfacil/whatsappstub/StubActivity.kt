package com.launcherfacil.whatsappstub

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.TextView

/** Muestra (y registra en logcat) qué llamada de WhatsApp se ha pedido. */
class StubActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = if (intent.type?.contains("video") == true) "VIDEOLLAMADA" else "LLAMADA DE VOZ"
        val text = "WhatsApp falso\n\n$kind\n\n${intent.data}"
        Log.i("WhatsAppStub", "$kind ${intent.data} ${intent.type}")
        setContentView(TextView(this).apply {
            this.text = text
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#075E54"))
        })
    }
}
