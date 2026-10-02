package com.launcherfacil.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class BatteryState(val percent: Int, val charging: Boolean)

private fun Intent.toBatteryState(): BatteryState {
    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, 100)
    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val percent = if (level >= 0 && scale > 0) level * 100 / scale else 0
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryState(percent, charging)
}

/** Estado de la batería; escucha ACTION_BATTERY_CHANGED mientras la pantalla está visible. */
@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(BatteryState(0, false)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                state = intent.toBatteryState()
            }
        }
        // El broadcast es "sticky": registerReceiver devuelve ya el último valor.
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { state = it.toBatteryState() }
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}

fun batteryColor(percent: Int): Color = when {
    percent > 50 -> BateriaAlta
    percent > 20 -> BateriaMedia
    else -> BateriaBaja
}

@Composable
fun BigBattery(modifier: Modifier = Modifier) {
    val battery = rememberBatteryState()
    val color = batteryColor(battery.percent)
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        BatteryIcon(battery, color, Modifier.size(width = 140.dp, height = 70.dp))
        Text(
            text = "${battery.percent}%",
            color = color,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BatteryIcon(battery: BatteryState, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val stroke = size.height * 0.09f
        val capWidth = size.width * 0.07f
        val bodyWidth = size.width - capWidth

        // Cuerpo y polo.
        drawRoundRect(
            color = TextoClaro,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = CornerRadius(size.height * 0.15f),
            style = Stroke(stroke)
        )
        drawRoundRect(
            color = TextoClaro,
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(capWidth, size.height * 0.4f),
            cornerRadius = CornerRadius(capWidth / 2)
        )

        // Relleno proporcional a la carga (con un mínimo visible aunque esté casi vacía).
        val gap = stroke * 1.8f
        val fill = (bodyWidth - gap * 2) * (battery.percent.coerceIn(3, 100) / 100f)
        drawRoundRect(
            color = color,
            topLeft = Offset(gap, gap),
            size = Size(fill, size.height - gap * 2),
            cornerRadius = CornerRadius(size.height * 0.08f)
        )

        // Rayo cuando está cargando.
        if (battery.charging) {
            val cx = bodyWidth / 2
            val h = size.height
            val bolt = Path().apply {
                moveTo(cx + h * 0.10f, h * 0.12f)
                lineTo(cx - h * 0.22f, h * 0.55f)
                lineTo(cx - h * 0.02f, h * 0.55f)
                lineTo(cx - h * 0.10f, h * 0.88f)
                lineTo(cx + h * 0.22f, h * 0.45f)
                lineTo(cx + h * 0.02f, h * 0.45f)
                close()
            }
            drawPath(bolt, Color.Black.copy(alpha = 0.55f), style = Stroke(stroke * 1.2f))
            drawPath(bolt, TextoClaro)
        }
    }
}
