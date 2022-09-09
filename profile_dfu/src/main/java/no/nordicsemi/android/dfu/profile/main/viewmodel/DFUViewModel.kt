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
import no.nordicsemi.android.common.navigation.NavigationManager
import no.nordicsemi.android.common.navigation.doNothing
import no.nordicsemi.android.dfu.analytics.*
import no.nordicsemi.android.dfu.profile.DfuSettingsScreen
import no.nordicsemi.android.dfu.profile.DfuWelcomeScreen
import no.nordicsemi.android.dfu.profile.main.data.*
import no.nordicsemi.android.dfu.profile.main.repository.DFURepository
import no.nordicsemi.android.dfu.profile.main.view.*
import no.nordicsemi.android.dfu.profile.main.view.DFUProgressViewEntity.Companion.createErrorStage
import no.nordicsemi.android.dfu.profile.scanner.ScannerDestination
import no.nordicsemi.android.dfu.profile.scanner.ScannerResult
import no.nordicsemi.android.dfu.profile.settings.repository.SettingsRepository
import no.nordicsemi.android.dfu.storage.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StateHolder @Inject constructor() {
    internal val state = MutableStateFlow(DFUViewState())

    fun clear() {
        state.value = DFUViewState()
    }
}

@HiltViewModel
internal class DFUViewModel @Inject constructor(
    private val stateHolder: StateHolder,
    private val repository: DFURepository,
    private val navigationManager: NavigationManager,
    private val analytics: DfuAnalytics,
    settingsRepository: SettingsRepository,
    externalFileDataSource: ExternalFileDataSource,
    deepLinkHandler: DeepLinkHandler,
) : ViewModel() {

    private val _state: MutableStateFlow<DFUViewState> = stateHolder.state
    val state = _state.asStateFlow()

    init {
        navigationManager.getResultForIds(ScannerDestination)
            .mapNotNull { it as? ScannerResult }
            .mapNotNull { it.device }
            .onEach { device ->
                repository.device = device
                _state.value = _state.value.copy(
                    deviceViewEntity = SelectedDeviceViewEntity(device),
                    progressViewEntity = WorkingProgressViewEntity()
                )
            }
            .launchIn(viewModelScope)

        repository.data
            .mapNotNull { it as? WorkingStatus }
            .mapNotNull { it.status }
            .onEach { status ->
                when (status) {
                    is EnablingDfu -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createDfuStage())
                    }
                    is Completed -> {
                        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createSuccessStage())
                        analytics.logEvent(DFUSuccessEvent)
                    }
                    is ProgressUpdate -> {
                        val newStatus = (_state.value.progressViewEntity as? WorkingProgressViewEntity)?.status?.createErrorStage("Aborted")
                        newStatus?.let {
                            _state.value = _state.value.copy(progressViewEntity = newStatus)
                        }
                    }
                    is Aborted -> {
                        val newStatus = (_state.value.progressViewEntity as? WorkingProgressViewEntity)?.status?.createErrorStage("Aborted")
                        newStatus?.let {
                            _state.value = _state.value.copy(progressViewEntity = newStatus)
                        }
                    }
                    is Error -> {
                        val newStatus = (_state.value.progressViewEntity as? WorkingProgressViewEntity)?.status?.createErrorStage(status.message)
                        newStatus?.let {
                            _state.value = _state.value.copy(progressViewEntity = newStatus)
                        }
                        analytics.logEvent(DFUErrorEvent(status.message))
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)

        settingsRepository.settings
            .onEach {
                _state.value = _state.value.copy(settings = it)

                if (it.showWelcomeScreen) {
                    navigationManager.navigateTo(DfuWelcomeScreen)
                }
            }
            .launchIn(viewModelScope)

        externalFileDataSource.fileResource
            .onEach {
                when (it) {
                    is FileDownloaded -> onZipFileSelected(it.uri)
                    FileError -> _state.value = _state.value.copy(
                        fileViewEntity = NotSelectedFileViewEntity(isRunning = false, isError = true),
                    )
                    LoadingFile -> _state.value = _state.value.copy(
                        fileViewEntity = NotSelectedFileViewEntity(isRunning = true, isError = false),
                    )
                    null -> doNothing()
                }
            }
            .launchIn(viewModelScope)

        deepLinkHandler.zipFile
            .onEach { it?.let { onZipFileSelected(it) } }
            .launchIn(viewModelScope)
    }

    private fun requestBluetoothDevice() {
        navigationManager.navigateTo(ScannerDestination)
    }

    fun onEvent(event: DFUViewEvent) {
        when (event) {
            OnDisconnectButtonClick -> navigationManager.navigateUp()
            OnInstallButtonClick -> onInstallButtonClick()
            OnAbortButtonClick -> repository.abort()
            is OnZipFileSelected -> onZipFileSelected(event.file)
            NavigateUp -> navigationManager.navigateUp()
            OnCloseButtonClick -> navigationManager.navigateUp()
            OnSelectDeviceButtonClick -> requestBluetoothDevice()
            OnSettingsButtonClick -> navigationManager.navigateTo(DfuSettingsScreen)
            OnLoggerButtonClick -> repository.openLogger()
        }
    }

    private fun onInstallButtonClick() = _state.value.settings?.let {
        repository.launch(it)
        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createBootloaderStage())
        analytics.logEvent(InstallationStartedEvent)
    }

    private fun onZipFileSelected(uri: Uri) {
        val zipFile = repository.setZipFile(uri)
        if (zipFile == null) {
            _state.value = _state.value.copy(fileViewEntity = NotSelectedFileViewEntity(true))
        } else {
            _state.value = _state.value.copy(
                fileViewEntity = SelectedFileViewEntity(zipFile),
                deviceViewEntity = NotSelectedDeviceViewEntity
            )
        }
        if (repository.device != null) {
            _state.value = _state.value.copy(
                progressViewEntity = WorkingProgressViewEntity(),
                deviceViewEntity = SelectedDeviceViewEntity(repository.device!!)
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
