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

import android.accounts.AccountManager
import android.accounts.OnAccountsUpdateListener
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.launcherfacil.app.R
import com.launcherfacil.app.contacts.GoogleAccount
import com.launcherfacil.app.contacts.isMasterSyncOn
import com.launcherfacil.app.contacts.loadGoogleAccounts
import com.launcherfacil.app.contacts.setContactsAutoSync
import com.launcherfacil.app.contacts.setMasterSync
import com.launcherfacil.app.contacts.syncContactsNow
import com.launcherfacil.app.inbox.rememberContentVersion

/**
 * Contactos desde una cuenta de Google: qué cuentas hay en el móvil, cuántos favoritos
 * trae cada una, si se sincronizan solas, y botones para sincronizar ya o añadir otra.
 */
@Composable
fun GoogleAccountsSection(onAddAccount: () -> Unit) {
    val context = LocalContext.current
    // Se recarga al llegar contactos nuevos y al añadir o quitar cuentas.
    val contactsVersion = rememberContentVersion(ContactsContract.RawContacts.CONTENT_URI)
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val manager = AccountManager.get(context)
        val listener = OnAccountsUpdateListener { refresh++ }
        val added = try {
            manager.addOnAccountsUpdatedListener(listener, null, false)
            true
        } catch (e: Exception) {
            false
        }
        onDispose { if (added) manager.removeOnAccountsUpdatedListener(listener) }
    }
    val accounts by produceState(emptyList<GoogleAccount>(), contactsVersion, refresh) {
        value = loadGoogleAccounts(context)
    }
    var masterSync by remember { mutableStateOf(isMasterSyncOn()) }
    var syncing by remember { mutableStateOf(false) }

    if (!masterSync) {
        Text("⚠ " + stringResource(R.string.settings_google_master_off), color = BateriaMedia, fontSize = 15.sp)
        ActionButton(stringResource(R.string.settings_google_master_on), {
            setMasterSync(true)
            masterSync = isMasterSyncOn()
        })
    }

    if (accounts.isEmpty()) {
        Text(stringResource(R.string.settings_google_none), color = TextoSuave, fontSize = 16.sp)
    }
    accounts.forEach { account ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(account.name, color = TextoClaro, fontSize = 18.sp)
            Text(stringResource(R.string.settings_google_favorites, account.favorites), color = TextoSuave, fontSize = 15.sp)
            SwitchRow(stringResource(R.string.settings_google_autosync), account.autoSync) { on ->
                setContactsAutoSync(account.name, on)
                refresh++
            }
        }
    }

    if (accounts.isNotEmpty()) {
        ActionButton(stringResource(R.string.settings_google_sync_now), {
            accounts.forEach { syncContactsNow(it.name) }
            syncing = true
        })
        if (syncing) Text(stringResource(R.string.settings_google_syncing), color = TextoSuave, fontSize = 15.sp)
    }
    ActionButton(stringResource(R.string.settings_google_add), onAddAccount)
}
