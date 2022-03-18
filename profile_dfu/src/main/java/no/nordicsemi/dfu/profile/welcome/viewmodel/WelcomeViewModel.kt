package no.nordicsemi.dfu.profile.welcome.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.nordicsemi.android.navigation.NavigationManager
import no.nordicsemi.dfu.profile.settings.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val navigationManager: NavigationManager,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val firstRun = settingsRepository.settings.map {
        it.showWelcomeScreen
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun navigateUp() {
        viewModelScope.launch {
            settingsRepository.tickWelcomeScreenShown()
            navigationManager.navigateUp()
        }
    }
}
