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

package no.nordicsemi.android.dfu.profile.main.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.common.navigation.NavigationResult
import no.nordicsemi.android.common.navigation.Navigator
import no.nordicsemi.android.dfu.analytics.*
import no.nordicsemi.android.dfu.profile.main.data.*
import no.nordicsemi.android.dfu.profile.main.repository.DFURepository
import no.nordicsemi.android.dfu.profile.main.view.*
import no.nordicsemi.android.dfu.profile.main.view.DFUProgressViewEntity.Companion.createErrorStage
import no.nordicsemi.android.dfu.profile.scanner.Scanner
import no.nordicsemi.android.dfu.profile.settings.DfuSettings
import no.nordicsemi.android.dfu.profile.welcome.DfuWelcome
import no.nordicsemi.android.dfu.settings.repository.SettingsRepository
import no.nordicsemi.android.dfu.storage.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class StateHolder @Inject constructor() {
    internal val state = MutableStateFlow(DFUViewState())

    fun clear() {
        state.value = DFUViewState()
    }
}

@HiltViewModel
internal class DFUViewModel @Inject constructor(
    private val stateHolder: StateHolder,
    private val repository: DFURepository,
    private val navigator: Navigator,
    private val analytics: DfuAnalytics,
    settingsRepository: SettingsRepository,
    externalFileDataSource: ExternalFileDataSource,
    deepLinkHandler: DeepLinkHandler,
) : ViewModel() {
    private val _state: MutableStateFlow<DFUViewState> = stateHolder.state
    val state = _state.asStateFlow()

    init {
        navigator.resultFrom(Scanner)
            // Filter out results of cancelled navigation.
            .mapNotNull { it as? NavigationResult.Success }
            .map { it.value }
            // Present the returned device.
            .onEach { target ->
                repository.target = target
                _state.value = _state.value.copy(
                    deviceViewEntity = SelectedDeviceViewEntity(target),
                    progressViewEntity = WorkingProgressViewEntity()
                )
            }
            .launchIn(viewModelScope)

        repository.data
            .mapNotNull { it as? DfuState.InProgress }
            .mapNotNull { it.status }
            .onEach { status ->
                when (status) {
                    is Starting -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createBootloaderStage())
                    }
                    is InitializingDFU -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createDfuStage())
                    }
                    is Uploading -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createInstallingStage(status))
                    }
                    is Completed -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createSuccessStage())
                        analytics.logEvent(DFUSuccessEvent)
                    }
                    is Aborted -> {
                        (_state.value.progressViewEntity as? WorkingProgressViewEntity)?.status
                            ?.createErrorStage("Aborted")
                            ?.also { progressEntityWithError ->
                                _state.value = _state.value.copy(progressViewEntity = progressEntityWithError)
                            }
                        analytics.logEvent(DFUAbortedEvent)
                    }
                    is Error -> {
                        (_state.value.progressViewEntity as? WorkingProgressViewEntity)?.status
                            ?.createErrorStage(status.message)
                            ?.also { progressEntityWithError ->
                                _state.value = _state.value.copy(progressViewEntity = progressEntityWithError)
                            }
                        analytics.logEvent(DFUErrorEvent(status.key))
                    }
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.settings
            .onEach {
                _state.value = _state.value.copy(settings = it)

                if (it.showWelcomeScreen) {
                    navigator.navigateTo(DfuWelcome)
                }
            }
            .launchIn(viewModelScope)

        externalFileDataSource.fileResource
            .onEach {
                when (it) {
                    is FileDownloaded -> onZipFileSelected(it.uri)
                    is FileError -> _state.value = _state.value.copy(
                        fileViewEntity = NotSelectedFileViewEntity(isRunning = false, isError = true),
                    )
                    is LoadingFile -> _state.value = _state.value.copy(
                        fileViewEntity = NotSelectedFileViewEntity(isRunning = true, isError = false),
                    )
                    null -> {}
                }
            }
            .launchIn(viewModelScope)

        deepLinkHandler.zipFile
            .onEach { it?.let { onZipFileSelected(it) } }
            .launchIn(viewModelScope)
    }

    private fun requestBluetoothDevice() {
        navigator.navigateTo(Scanner)
    }

    fun onEvent(event: DFUViewEvent) {
        when (event) {
            OnDisconnectButtonClick -> navigator.navigateUp()
            OnInstallButtonClick -> onInstallButtonClick()
            OnAbortButtonClick -> repository.abort()
            is OnZipFileSelected -> onZipFileSelected(event.file)
            NavigateUp -> navigator.navigateUp()
            OnCloseButtonClick -> navigator.navigateUp()
            OnSelectDeviceButtonClick -> requestBluetoothDevice()
            OnSettingsButtonClick -> navigator.navigateTo(DfuSettings)
            OnLoggerButtonClick -> repository.openLogger()
        }
    }

    private fun onInstallButtonClick() = _state.value.settings?.let {
        analytics.logEvent(InstallationStartedEvent)
        repository.launch(it)
    }

    private fun onZipFileSelected(uri: Uri) {
        // Check if the selected file is valid.
        val file = repository.setZipFile(uri)
        file?.let { zipFile ->
            _state.value = _state.value.copy(
                fileViewEntity = SelectedFileViewEntity(zipFile),
                deviceViewEntity = repository.target
                    ?.let { SelectedDeviceViewEntity(it) }
                    ?: NotSelectedDeviceViewEntity,
            )
            analytics.logEvent(FileSelectedEvent(zipFile.size))
        } ?: run { // If not, show an error.
            _state.value = _state.value.copy(
                fileViewEntity = NotSelectedFileViewEntity(true)
            )
        }
    }

    override fun onCleared() {
        super.onCleared()

        if (!repository.isRunning()) {
            stateHolder.clear()
            repository.release()
        }
    }
}
