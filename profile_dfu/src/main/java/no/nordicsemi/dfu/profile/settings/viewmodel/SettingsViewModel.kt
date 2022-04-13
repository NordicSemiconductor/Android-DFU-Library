package no.nordicsemi.dfu.profile.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.nordicsemi.android.analytics.*
import no.nordicsemi.android.navigation.NavigationManager
import no.nordicsemi.dfu.profile.DfuWelcomeScreen
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.dfu.profile.settings.repository.SettingsRepository
import no.nordicsemi.dfu.profile.settings.view.*
import no.nordicsemi.ui.scanner.ui.exhaustive
import javax.inject.Inject

private const val INFOCENTER_LINK = "https://infocenter.nordicsemi.com/topic/sdk_nrf5_v17.1.0/examples_bootloader.html"

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val navigationManager: NavigationManager,
    private val analytics: AppAnalytics
) : ViewModel() {

    val state = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, DFUSettings())

    fun onEvent(event: SettingsScreenViewEvent) {
        when (event) {
            NavigateUp -> navigationManager.navigateUp()
            OnExternalMcuDfuSwitchClick -> onExternalMcuDfuSwitchClick()
            OnKeepBondInformationSwitchClick -> onKeepBondSwitchClick()
            OnPacketsReceiptNotificationSwitchClick -> onPacketsReceiptNotificationSwitchClick()
            OnAboutAppClick -> navigationManager.openLink(INFOCENTER_LINK)
            OnDisableResumeSwitchClick -> onDisableResumeSwitchClick()
            OnForceScanningAddressesSwitchClick -> onForceScanningAddressesSwitchClick()
            OnShowWelcomeClick -> navigationManager.navigateTo(DfuWelcomeScreen)
            is OnNumberOfPocketsChange -> onNumberOfPocketsChange(event.numberOfPockets)
        }.exhaustive
    }

    private fun onExternalMcuDfuSwitchClick() {
        val currentValue = state.value.externalMcuDfu
        val newSettings = state.value.copy(externalMcuDfu = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(ExternalMCUSettingsEvent(newSettings.externalMcuDfu))
    }

    private fun onKeepBondSwitchClick() {
        val currentValue = state.value.keepBondInformation
        val newSettings = state.value.copy(keepBondInformation = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(KeepBondSettingsEvent(newSettings.keepBondInformation))
    }

    private fun onPacketsReceiptNotificationSwitchClick() {
        val currentValue = state.value.packetsReceiptNotification
        val newSettings = state.value.copy(packetsReceiptNotification = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(PacketsReceiptNotificationSettingsEvent(newSettings.packetsReceiptNotification))
    }

    private fun onDisableResumeSwitchClick() {
        val currentValue = state.value.disableResume
        val newSettings = state.value.copy(disableResume = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(DisableResumeSettingsEvent(newSettings.disableResume))
    }

    private fun onForceScanningAddressesSwitchClick() {
        val currentValue = state.value.forceScanningInLegacyDfu
        val newSettings = state.value.copy(forceScanningInLegacyDfu = !currentValue)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(ForceScanningSettingsEvent(newSettings.forceScanningInLegacyDfu))
    }

    private fun onNumberOfPocketsChange(numberOfPockets: Int) {
        val newSettings = state.value.copy(numberOfPackets = numberOfPockets)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(NumberOfPacketsSettingsEvent(newSettings.numberOfPackets))
    }
}
