package com.launcherfacil.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.Contact
import com.launcherfacil.app.contacts.loadContactPhoto
import com.launcherfacil.app.contacts.loadFavoriteContacts

@Composable
fun ContactsScreen(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onContactClick: (Contact) -> Unit,
    onHome: () -> Unit
) {
    val context = LocalContext.current
    val contacts by produceState<List<Contact>?>(null, hasPermission) {
        value = if (hasPermission) loadFavoriteContacts(context) else emptyList()
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
            !hasPermission -> Message(
                "Hace falta permiso para leer los contactos.",
                actionText = "Dar permiso",
                onAction = onRequestPermission
            )
            list == null -> Unit // Cargando: es casi instantáneo.
            list.isEmpty() -> Message(
                "No hay contactos favoritos.\nMarca con ★ en la app Contactos las personas que deben aparecer aquí."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.id }) { contact ->
                    ContactCard(contact, onClick = { onContactClick(contact) })
                }
            }
        }
    }
}

@Composable
private fun ContactCard(contact: Contact, onClick: () -> Unit) {
    val context = LocalContext.current
    val photo by produceState<ImageBitmap?>(null, contact.id) {
        value = loadContactPhoto(context, contact.id)
    }
    BigTile(VerdeLlamar, onClick, Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = photo
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        tint = TextoClaro,
                        modifier = Modifier.fillMaxSize(0.7f)
                    )
                }
            }
            // El nombre es para el cuidador; el usuario se guía por la foto.
            Text(
                text = contact.name,
                color = TextoClaro,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun Message(text: String, actionText: String? = null, onAction: () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text, color = TextoSuave, fontSize = 24.sp, textAlign = TextAlign.Center)
        if (actionText != null) {
            Spacer(Modifier.height(24.dp))
            BigTile(AzulTeclado, onAction, Modifier.size(width = 260.dp, height = 80.dp)) {
                Text(actionText, color = TextoClaro, fontSize = 24.sp)
            }
        }
    }
}
