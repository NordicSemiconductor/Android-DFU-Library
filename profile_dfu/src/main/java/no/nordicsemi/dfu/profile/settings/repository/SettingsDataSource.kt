package no.nordicsemi.dfu.profile.settings.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.flow.map
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import javax.inject.Inject
import javax.inject.Singleton

private val PACKETS_RECEIPT_NOTIFICATION_KEY = booleanPreferencesKey("packets_receipt")
private val KEEP_BOND_KEY = booleanPreferencesKey("keep_bond")
private val EXTERNAL_MCU_KEY = booleanPreferencesKey("external_mcu")

@Singleton
class SettingsDataSource @Inject constructor(
    @ApplicationContext
    private val context: Context
) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val settings = context.dataStore.data.map { it.toSettings() }

    suspend fun storeSettings(settings: DFUSettings) {
        context.dataStore.edit {
            it[PACKETS_RECEIPT_NOTIFICATION_KEY] = settings.packetsReceiptNotification
            it[KEEP_BOND_KEY] = settings.keepBondInformation
            it[EXTERNAL_MCU_KEY] = settings.externalMcuDfu
        }
    }

    private fun Preferences.toSettings(): DFUSettings {
        return DFUSettings(
            this[PACKETS_RECEIPT_NOTIFICATION_KEY] ?: false,
            this[KEEP_BOND_KEY] ?: false,
            this[EXTERNAL_MCU_KEY] ?: false,
        )
    }
}
