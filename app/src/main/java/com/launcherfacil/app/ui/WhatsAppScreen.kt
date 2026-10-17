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

package com.launcherfacil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.WhatsAppContact
import com.launcherfacil.app.contacts.isWhatsAppInstalled
import com.launcherfacil.app.contacts.loadFavoriteWhatsAppContacts

/** Favoritos con WhatsApp: tocar la foto inicia la videollamada. */
@Composable
fun WhatsAppScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onVideoCall: (WhatsAppContact) -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val installed = context.isWhatsAppInstalled()
    val contacts by produceState<List<WhatsAppContact>?>(null, hasPermission) {
        value = if (hasPermission) loadFavoriteWhatsAppContacts(context) else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        HomeBar(onHome)
        Spacer(Modifier.height(16.dp))

        val list = contacts
        when {
            !installed -> CaregiverMessage("WhatsApp no está instalado en este teléfono.")
            !hasPermission -> CaregiverMessage(
                "Hace falta permiso para leer los contactos.",
                actionText = "Dar permiso",
                onAction = onRequestPermission
            )
            list == null -> Unit
            list.isEmpty() -> CaregiverMessage(
                "Ningún favorito está marcado con WhatsApp.\nMárcalos en la configuración del cuidador (reloj 5 s → PIN → \"Tienen WhatsApp\")."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.contactId }) { contact ->
                    BigTile(VerdeWhatsApp, { onVideoCall(contact) }, Modifier.fillMaxWidth()) {
                        Box {
                            ContactPhoto(contact.contactId, contact.name)
                            VideoBadge(Modifier.align(Alignment.TopEnd).padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Cámara pequeña en la esquina de la foto: indica que es una videollamada. */
@Composable
private fun VideoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(52.dp).background(AzulTeclado, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_videocam),
            contentDescription = null,
            tint = TextoClaro,
            modifier = Modifier.size(32.dp)
        )
    }
}
