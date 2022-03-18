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
