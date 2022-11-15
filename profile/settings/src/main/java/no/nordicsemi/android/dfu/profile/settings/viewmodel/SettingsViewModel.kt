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

package no.nordicsemi.android.dfu.profile.settings.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import no.nordicsemi.android.common.navigation.Navigator
import no.nordicsemi.android.dfu.analytics.*
import no.nordicsemi.android.dfu.profile.settings.view.*
import no.nordicsemi.android.dfu.profile.welcome.DfuWelcome
import no.nordicsemi.android.dfu.settings.domain.DFUSettings
import no.nordicsemi.android.dfu.settings.repository.SettingsRepository
import javax.inject.Inject

private val INFOCENTER_LINK = Uri.parse("https://infocenter.nordicsemi.com/topic/sdk_nrf5_v17.1.0/examples_bootloader.html")

@HiltViewModel
internal class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val navigator: Navigator,
    private val analytics: DfuAnalytics
) : ViewModel() {

    val state = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, DFUSettings())

    fun onEvent(event: SettingsScreenViewEvent) {
        when (event) {
            NavigateUp -> navigator.navigateUp()
            OnResetButtonClick -> restoreDefaultSettings()
            OnAboutAppClick -> navigator.navigateTo(DfuWelcome)
            OnAboutDfuClick -> navigator.open(INFOCENTER_LINK)
            OnExternalMcuDfuSwitchClick -> onExternalMcuDfuSwitchClick()
            OnKeepBondInformationSwitchClick -> onKeepBondSwitchClick()
            OnPacketsReceiptNotificationSwitchClick -> onPacketsReceiptNotificationSwitchClick()
            OnDisableResumeSwitchClick -> onDisableResumeSwitchClick()
            OnForceScanningAddressesSwitchClick -> onForceScanningAddressesSwitchClick()
            is OnNumberOfPocketsChange -> onNumberOfPocketsChange(event.numberOfPockets)
            is OnPrepareDataObjectDelayChange -> onPrepareDataObjectDelayChange(event.delay)
            is OnRebootTimeChange -> onRebootTimeChange(event.time)
            is OnScanTimeoutChange -> onScanTimeoutChange(event.timeout)
        }
    }

    private fun restoreDefaultSettings() {
        viewModelScope.launch {
            repository.storeSettings(DFUSettings().copy(showWelcomeScreen = false))
        }
        analytics.logEvent(ResetSettingsEvent)
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

    private fun onPrepareDataObjectDelayChange(delay: Int) {
        val newSettings = state.value.copy(prepareDataObjectDelay = delay)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(PrepareDataObjectDelaySettingsEvent(newSettings.prepareDataObjectDelay))
    }

    private fun onRebootTimeChange(rebootTime: Int) {
        val newSettings = state.value.copy(rebootTime = rebootTime)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(RebootTimeSettingsEvent(newSettings.rebootTime))
    }

    private fun onScanTimeoutChange(scanTimeout: Int) {
        val newSettings = state.value.copy(scanTimeout = scanTimeout)
        viewModelScope.launch {
            repository.storeSettings(newSettings)
        }
        analytics.logEvent(ScanTimeoutSettingsEvent(newSettings.scanTimeout))
    }
}
