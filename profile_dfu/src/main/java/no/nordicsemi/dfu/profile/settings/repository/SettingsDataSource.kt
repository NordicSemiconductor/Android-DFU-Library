/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.dfu.profile.settings.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.map
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.dfu.profile.settings.domain.NUMBER_OF_POCKETS_INITIAL
import javax.inject.Inject
import javax.inject.Singleton

private val PACKETS_RECEIPT_NOTIFICATION_KEY = booleanPreferencesKey("packets_receipt")
private val KEEP_BOND_KEY = booleanPreferencesKey("keep_bond")
private val EXTERNAL_MCU_KEY = booleanPreferencesKey("external_mcu")
private val SHOW_WELCOME_KEY = booleanPreferencesKey("show_welcome")
private val DISABLE_RESUME = booleanPreferencesKey("disable_resume")
private val FORCE_SCANNING_ADDRESS = booleanPreferencesKey("force_scanning_address")
private val NUMBER_OF_POCKETS_KEY = intPreferencesKey("number_of_pockets")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val settings = context.dataStore.data.map { it.toSettings() }

    suspend fun tickWelcomeScreenShown() {
        context.dataStore.edit {
            it[SHOW_WELCOME_KEY] = false
        }
    }

    suspend fun storeSettings(settings: DFUSettings) {
        context.dataStore.edit {
            it[PACKETS_RECEIPT_NOTIFICATION_KEY] = settings.packetsReceiptNotification
            it[NUMBER_OF_POCKETS_KEY] = settings.numberOfPackets
            it[KEEP_BOND_KEY] = settings.keepBondInformation
            it[EXTERNAL_MCU_KEY] = settings.externalMcuDfu
            it[DISABLE_RESUME] = settings.disableResume
            it[FORCE_SCANNING_ADDRESS] = settings.forceScanningInLegacyDfu
            it[SHOW_WELCOME_KEY] = settings.showWelcomeScreen
        }
    }

    private fun Preferences.toSettings(): DFUSettings {
        return DFUSettings(
            this[PACKETS_RECEIPT_NOTIFICATION_KEY] ?: false,
            this[NUMBER_OF_POCKETS_KEY] ?: NUMBER_OF_POCKETS_INITIAL,
            this[KEEP_BOND_KEY] ?: false,
            this[EXTERNAL_MCU_KEY] ?: false,
            this[DISABLE_RESUME] ?: false,
            this[FORCE_SCANNING_ADDRESS] ?: false,
            this[SHOW_WELCOME_KEY] ?: true,
        )
    }
}
