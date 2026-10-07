package com.launcherfacil.app.ui

import android.provider.CallLog
import android.provider.Telephony
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.findContactByNumber
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.inbox.MissedCall
import com.launcherfacil.app.inbox.SmsMessage
import com.launcherfacil.app.inbox.deleteMissedCalls
import com.launcherfacil.app.inbox.formatWhen
import com.launcherfacil.app.inbox.hideSms
import com.launcherfacil.app.inbox.loadInboxSms
import com.launcherfacil.app.inbox.loadMissedCalls
import com.launcherfacil.app.inbox.markMissedCallsSeen
import com.launcherfacil.app.inbox.markSmsSeen
import com.launcherfacil.app.inbox.rememberContentVersion
import kotlinx.coroutines.launch

val NaranjaMensajes = Color(0xFFEF6C00)
val MoradoPerdidas = Color(0xFF6A1B9A)

// --- Mensajes ---

/** Lista de SMS recibidos; al tocar uno se ve en grande con la papelera para borrarlo. */
@Composable
fun MessagesScreen(onHome: () -> Unit) {
    val context = LocalContext.current
    val version = rememberContentVersion(Telephony.Sms.CONTENT_URI)
    var reload by remember { mutableStateOf(0) }
    val messages by produceState<List<SmsMessage>?>(null, version, reload) {
        value = loadInboxSms(context)
    }
    var open by remember { mutableStateOf<SmsMessage?>(null) }
    LaunchedEffect(Unit) { markSmsSeen(context) }

    val current = open
    if (current != null) {
        BackHandler { open = null }
        MessageDetail(
            message = current,
            onDelete = {
                hideSms(context, current.id)
                open = null
                reload++
            },
            onHome = onHome
        )
        return
    }

    ListScaffold(onHome) {
        val list = messages
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyIcon(R.drawable.ic_sms, NaranjaMensajes)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.id }) { sms ->
                    PersonRow(sms.address, sms.date, NaranjaMensajes, onClick = { open = sms }) {
                        Text(
                            sms.body,
                            color = TextoClaro,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageDetail(message: SmsMessage, onDelete: () -> Unit, onHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HomeBar(onHome)
        PersonRow(message.address, message.date, NaranjaMensajes, onClick = null) {}
        Text(
            message.body,
            color = TextoClaro,
            fontSize = 30.sp,
            lineHeight = 40.sp,
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
        )
        BigTile(RojoSos, onDelete, Modifier.fillMaxWidth().height(110.dp)) {
            TileIcon(R.drawable.ic_delete, 0.6f)
        }
    }
}

// --- Llamadas perdidas ---

/** Llamadas perdidas; tocar una devuelve la llamada. Al entrar se marcan como vistas. */
@Composable
fun MissedCallsScreen(onCallBack: (String) -> Unit, onHome: () -> Unit) {
    val context = LocalContext.current
    val version = rememberContentVersion(CallLog.Calls.CONTENT_URI)
    val calls by produceState<List<MissedCall>?>(null, version) { value = loadMissedCalls(context) }
    LaunchedEffect(Unit) { markMissedCallsSeen(context) }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        BackHandler { confirmClear = false }
        ConfirmDelete(
            onConfirm = {
                confirmClear = false
                scope.launch { deleteMissedCalls(context) }
            },
            onCancel = { confirmClear = false }
        )
        return
    }

    ListScaffold(onHome) {
        val list = calls
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyIcon(R.drawable.ic_phone_missed, MoradoPerdidas)
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.id }) { call ->
                    PersonRow(
                        call.number, call.date, MoradoPerdidas,
                        onClick = { if (call.number.isNotBlank()) onCallBack(call.number) }
                    ) {
                        // Botón verde de devolver la llamada, para que se entienda sin leer.
                        Icon(
                            painter = painterResource(R.drawable.ic_call),
                            contentDescription = null,
                            tint = VerdeLlamar,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
                // Al final: papelera grande para borrar todo el historial (pide confirmación).
                item(key = "clear") {
                    BigTile(RojoSos, { confirmClear = true }, Modifier.fillMaxWidth().height(110.dp)) {
                        TileIcon(R.drawable.ic_delete, 0.6f)
                    }
                }
            }
        }
    }
}

/** Confirmación sin texto: papelera grande arriba, y abajo ✗ (no) y ✓ (sí, borrar). */
@Composable
private fun ConfirmDelete(onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(R.drawable.ic_delete), null,
                tint = RojoSos, modifier = Modifier.size(220.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BigTile(GrisBoton, onCancel, Modifier.weight(1f).fillMaxSize()) {
                TileIcon(R.drawable.ic_close, 0.6f)
            }
            BigTile(RojoSos, onConfirm, Modifier.weight(1f).fillMaxSize()) {
                TileIcon(R.drawable.ic_check, 0.6f)
            }
        }
    }
}

// --- Piezas comunes ---

@Composable
private fun ListScaffold(onHome: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        HomeBar(onHome)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Fila con la foto de la persona (o silueta), su nombre o número pequeño, la hora y más. */
@Composable
private fun PersonRow(
    number: String,
    date: Long,
    color: Color,
    onClick: (() -> Unit)?,
    extra: @Composable () -> Unit
) {
    val context = LocalContext.current
    val contact by produceState<Contact?>(null, number) {
        value = if (number.isNotBlank()) findContactByNumber(context, number) else null
    }
    val photo by produceState<ImageBitmap?>(null, contact?.id) {
        value = contact?.let { loadContactPhoto(context, it.id) }
    }
    val is24h = DateFormat.is24HourFormat(context)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(color.copy(alpha = 0.35f))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(20.dp)).background(color),
            contentAlignment = Alignment.Center
        ) {
            val bitmap = photo
            if (bitmap != null) {
                Image(bitmap, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(
                    painterResource(R.drawable.ic_person), null,
                    tint = TextoClaro, modifier = Modifier.fillMaxSize(0.75f)
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(
                contact?.name ?: number.ifBlank { "?" },
                color = TextoClaro,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(formatWhen(date, is24h), color = TextoSuave, fontSize = 18.sp)
            extra()
        }
    }
}

/** Lista vacía: el icono grande, sin texto. */
@Composable
private fun EmptyIcon(icon: Int, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            painterResource(icon), null,
            tint = color.copy(alpha = 0.5f), modifier = Modifier.size(180.dp)
        )
    }
}
