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

package com.launcherfacil.app.contacts

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.ContactsContract.RawContacts
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Contactos desde una cuenta de Google: el cuidador los crea en contacts.google.com
 * (nombre, foto y teléfono) y los marca con la estrella; Google los sincroniza con el
 * móvil y la app los muestra como cualquier otro favorito (★).
 */
const val GOOGLE_ACCOUNT_TYPE = "com.google"
private const val TAG = "GoogleAccounts"

data class GoogleAccount(val name: String, val favorites: Int, val autoSync: Boolean)

/** Cuentas de Google del móvil, con cuántos favoritos trae cada una y si se sincronizan solas. */
suspend fun loadGoogleAccounts(context: Context): List<GoogleAccount> = withContext(Dispatchers.IO) {
    val favorites = mutableMapOf<String, Int>()
    val names = linkedSetOf<String>()
    try {
        context.contentResolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.STARRED),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.DELETED} = 0",
            arrayOf(GOOGLE_ACCOUNT_TYPE), null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                names += name
                if (c.getInt(1) == 1) favorites[name] = (favorites[name] ?: 0) + 1
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin permiso de contactos", e)
    }
    // Cuentas recién añadidas que aún no han traído ningún contacto.
    try {
        AccountManager.get(context).getAccountsByType(GOOGLE_ACCOUNT_TYPE).forEach { names += it.name }
    } catch (e: SecurityException) {
        Log.w(TAG, "Sin acceso a las cuentas", e)
    }
    names.map { name ->
        GoogleAccount(
            name = name,
            favorites = favorites[name] ?: 0,
            autoSync = ContentResolver.getSyncAutomatically(googleAccount(name), ContactsContract.AUTHORITY)
        )
    }
}

private fun googleAccount(name: String) = Account(name, GOOGLE_ACCOUNT_TYPE)

/** ¿Está activada la sincronización automática de todo el móvil? Sin ella nada llega solo. */
fun isMasterSyncOn(): Boolean = ContentResolver.getMasterSyncAutomatically()

fun setMasterSync(on: Boolean) = ContentResolver.setMasterSyncAutomatically(on)

/** Activa o desactiva que los contactos de la cuenta se sincronicen solos. */
fun setContactsAutoSync(name: String, on: Boolean) {
    val account = googleAccount(name)
    ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1)
    ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, on)
    if (on) syncContactsNow(name)
}

/** Pide a Google que traiga ya los cambios (necesita datos móviles o Wi-Fi). */
fun syncContactsNow(name: String) {
    val extras = Bundle().apply {
        putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
    }
    ContentResolver.requestSync(googleAccount(name), ContactsContract.AUTHORITY, extras)
}
