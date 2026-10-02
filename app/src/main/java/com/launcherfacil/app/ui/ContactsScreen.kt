package com.launcherfacil.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.contacts.Contact
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
            !hasPermission -> CaregiverMessage(
                "Hace falta permiso para leer los contactos.",
                actionText = "Dar permiso",
                onAction = onRequestPermission
            )
            list == null -> Unit // Cargando: es casi instantáneo.
            list.isEmpty() -> CaregiverMessage(
                "No hay contactos favoritos.\nMarca con ★ en la app Contactos las personas que deben aparecer aquí."
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(list, key = { it.id }) { contact ->
                    BigTile(VerdeLlamar, { onContactClick(contact) }, Modifier.fillMaxWidth()) {
                        ContactPhoto(contact.id, contact.name)
                    }
                }
            }
        }
    }
}
