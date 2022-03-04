package no.nordicsemi.dfu.profile.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.nordicsemi.android.navigation.NavigationManager
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.dfu.profile.settings.repository.SettingsRepository
import no.nordicsemi.dfu.profile.settings.view.*
import no.nordicsemi.ui.scanner.ui.exhaustive
import javax.inject.Inject

private const val INFOCENTER_LINK = "https://infocenter.nordicsemi.com/topic/sdk_nrf5_v16.0.0/examples_bootloader.html?cp=7_1_4_4"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val navigationManager: NavigationManager
) : ViewModel() {

    val state = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, DFUSettings())

    fun onEvent(event: SettingsScreenViewEvent) {
        when (event) {
            NavigateUp -> navigationManager.navigateUp()
            OnExternalMcuDfuSwitchClick -> onExternalMcuDfuSwitchClick()
            OnKeepBondInformationSwitchClick -> onKeepBondSwitchClick()
            OnPacketsReceiptNotificationSwitchClick -> onPacketsReceiptNotificationSwitchClick()
            OnAboutAppClick -> navigationManager.openLink(INFOCENTER_LINK)
        }.exhaustive
    }

    private fun onExternalMcuDfuSwitchClick() {
        val currentValue = state.value.externalMcuDfu
        val newSettings = state.value.copy(externalMcuDfu = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
    }

    private fun onKeepBondSwitchClick() {
        val currentValue = state.value.keepBondInformation
        val newSettings = state.value.copy(keepBondInformation = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
    }

    private fun onPacketsReceiptNotificationSwitchClick() {
        val currentValue = state.value.packetsReceiptNotification
        val newSettings = state.value.copy(packetsReceiptNotification = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
    }
}
