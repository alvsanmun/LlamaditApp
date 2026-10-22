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

package com.launcherfacil.app.whatsapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import com.launcherfacil.app.MainActivity
import com.launcherfacil.app.R

/**
 * La casa para volver a LlamaditApp mientras se usa WhatsApp completo. Para tapar lo
 * menos posible es una pestaña pequeña pegada al borde derecho, a un tercio de la
 * altura: por debajo de la barra de arriba de WhatsApp y por encima del teclado y de
 * la caja para escribir. Semitransparente, con borde blanco para verse sobre fondo
 * claro y oscuro.
 *
 * Necesita el permiso "Mostrar sobre otras aplicaciones" (lo da el instalador o el
 * cuidador desde el panel). Sin él, WhatsApp se abre igual y se vuelve con Atrás.
 */
object WhatsAppHomeButton {
    private const val TAG = "WhatsAppHomeButton"
    private const val WIDTH_DP = 52
    private const val HEIGHT_DP = 68
    private const val TOP_FRACTION = 0.33f

    private var view: View? = null

    fun canShow(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun show(context: Context) {
        if (view != null || !canShow(context)) return
        val wm = context.getSystemService(WindowManager::class.java) ?: return
        val density = context.resources.displayMetrics.density
        val radius = 20 * density
        val button = ImageView(context).apply {
            setImageResource(R.drawable.ic_home)
            setColorFilter(Color.WHITE)
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            // Esquinas redondeadas solo a la izquierda: queda pegada al borde.
            background = GradientDrawable().apply {
                setColor(0xE6455A64.toInt())
                cornerRadii = floatArrayOf(radius, radius, 0f, 0f, 0f, 0f, radius, radius)
                setStroke((2 * density).toInt(), Color.WHITE)
            }
            elevation = 6 * density
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                backToLauncher(context)
            }
        }
        val params = WindowManager.LayoutParams(
            (WIDTH_DP * density).toInt(),
            (HEIGHT_DP * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.END or Gravity.TOP
            y = (context.resources.displayMetrics.heightPixels * TOP_FRACTION).toInt()
        }
        try {
            wm.addView(button, params)
            view = button
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo mostrar la casa sobre WhatsApp", e)
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        try {
            v.context.getSystemService(WindowManager::class.java)?.removeView(v)
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo quitar la casa", e)
        }
    }

    private fun backToLauncher(context: Context) {
        hide()
        try {
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo volver al launcher", e)
        }
    }
}
