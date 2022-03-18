package no.nordicsemi.dfu.profile.settings.repository

import dagger.hilt.android.scopes.ViewModelScoped
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import javax.inject.Inject

@ViewModelScoped
class SettingsRepository @Inject constructor(
    private val settingsDataSource: SettingsDataSource
) {

    val settings = settingsDataSource.settings

    suspend fun storeSettings(settings: DFUSettings) {
        settingsDataSource.storeSettings(settings)
    }

    suspend fun tickWelcomeScreenShown() {
        settingsDataSource.tickWelcomeScreenShown()
    }
}
