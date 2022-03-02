package no.nordicsemi.dfu.profile.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.navigation.*
import no.nordicsemi.dfu.profile.data.*
import no.nordicsemi.dfu.profile.view.*
import no.nordicsemi.dfu.profile.view.DFUProgressViewEntity.Companion.createErrorStage
import no.nordicsemi.ui.scanner.ScannerDestinationId
import no.nordicsemi.ui.scanner.ui.exhaustive
import no.nordicsemi.ui.scanner.ui.getDevice
import javax.inject.Inject

@HiltViewModel
internal class DFUViewModel @Inject constructor(
    private val repository: DFURepository,
    private val navigationManager: NavigationManager
) : ViewModel() {

    private val _state = MutableStateFlow(DFUViewState())
    val state = _state.asStateFlow()

    init {
        repository.data.onEach {
            ((it as? WorkingStatus)?.status as? EnablingDfu)?.let {
                _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createDfuStage())
            }

            ((it as? WorkingStatus)?.status as? Completed)?.let {
                _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createSuccessStage())
            }

            ((it as? WorkingStatus)?.status as? ProgressUpdate)?.let {
                _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createInstallingStage(it.progress))
            }

            ((it as? WorkingStatus)?.status as? Aborted)?.let {
                val newStatus = (_state.value.progressViewEntity as WorkingProgressViewEntity).status.createErrorStage("Aborted")
                _state.value = _state.value.copy(progressViewEntity = newStatus)
            }

            ((it as? WorkingStatus)?.status as? Error)?.let {
                val newStatus = (_state.value.progressViewEntity as WorkingProgressViewEntity).status.createErrorStage(it.message)
                _state.value = _state.value.copy(progressViewEntity = newStatus)
            }
        }.launchIn(viewModelScope)
    }

    private fun requestBluetoothDevice() {
        navigationManager.navigateTo(ScannerDestinationId)

        navigationManager.recentResult.onEach {
            if (it.destinationId == ScannerDestinationId) {
                handleArgs(it)
            }
        }.launchIn(viewModelScope)
    }

    private fun handleArgs(args: DestinationResult?) {
        when (args) {
            is CancelDestinationResult -> { /* do nothing */
            }
            is SuccessDestinationResult -> {
                repository.device = args.getDevice()
                _state.value = _state.value.copy(
                    deviceViewEntity = SelectedDeviceViewEntity(args.getDevice()),
                    progressViewEntity = WorkingProgressViewEntity()
                )
            }
            null -> navigationManager.navigateTo(ScannerDestinationId)
        }.exhaustive
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
        }.exhaustive
    }

    private fun onInstallButtonClick() {
        repository.launch(viewModelScope)
        _state.value = _state.value.copy(progressViewEntity = DFUProgressViewEntity.createBootloaderStage())
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
        repository.release()
    }
}
