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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.launcherfacil.app.R
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
                stringResource(R.string.contacts_permission_needed),
                actionText = stringResource(R.string.give_permission),
                onAction = onRequestPermission
            )
            list == null -> Unit // Cargando: es casi instantáneo.
            list.isEmpty() -> CaregiverMessage(
                stringResource(R.string.no_favorites)
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
