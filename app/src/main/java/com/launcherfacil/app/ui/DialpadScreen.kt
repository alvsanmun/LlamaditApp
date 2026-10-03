package com.launcherfacil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R

// Con menos de 3 cifras Android lo trata como código USSD; 112 o 091 sí funcionan.
private const val MIN_DIGITS = 3
private const val MAX_DIGITS = 15

@Composable
fun DialpadScreen(onCall: (String) -> Unit, onHome: () -> Unit) {
    var number by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeBar(onHome)

        // Número marcado: si no cabe, se muestran las últimas cifras.
        Text(
            text = number.takeLast(11),
            color = TextoClaro,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().height(80.dp).padding(top = 4.dp)
        )

        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
        for (row in rows) {
            KeyRow {
                for (digit in row) {
                    DigitKey(digit, Modifier.weight(1f)) {
                        if (number.length < MAX_DIGITS) number += digit
                    }
                }
            }
        }
        KeyRow {
            // Borrar: toque = última cifra; pulsación larga = todo.
            KeyBox(
                color = GrisBoton,
                onClick = { number = number.dropLast(1) },
                onLongClick = { number = "" },
                modifier = Modifier.weight(1f)
            ) { TileIcon(R.drawable.ic_backspace, 0.5f) }
            DigitKey("0", Modifier.weight(1f)) {
                if (number.length < MAX_DIGITS) number += "0"
            }
            KeyBox(
                color = VerdeLlamar,
                onClick = { if (number.length >= MIN_DIGITS) onCall(number) },
                modifier = Modifier.weight(1f)
            ) { TileIcon(R.drawable.ic_call, 0.5f) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
internal fun ColumnScope.KeyRow(
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
internal fun DigitKey(digit: String, modifier: Modifier, onClick: () -> Unit) {
    KeyBox(color = AzulTeclado, onClick = onClick, modifier = modifier) {
        Text(digit, color = TextoClaro, fontSize = 48.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun KeyBox(
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .combinedClickable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
                onLongClick = onLongClick?.let {
                    {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        it()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
