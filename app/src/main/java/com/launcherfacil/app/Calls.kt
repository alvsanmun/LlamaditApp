package com.launcherfacil.app

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Llama directamente (requiere CALL_PHONE concedido). */
fun Context.placeCall(number: String) {
    val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}

/** Abre el marcador con el 112: Android no deja que una app lo llame directamente. */
fun Context.dialEmergency() {
    val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", "112", null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
