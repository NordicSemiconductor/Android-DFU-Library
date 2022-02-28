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
import no.nordicsemi.dfu.profile.data.DFURepository
import no.nordicsemi.dfu.profile.data.DFU_SERVICE_UUID
import no.nordicsemi.dfu.profile.data.IdleStatus
import no.nordicsemi.dfu.profile.view.*
import no.nordicsemi.dfu.profile.view.components.NotSelectedFileViewEntity
import no.nordicsemi.dfu.profile.view.components.SelectedFileViewEntity
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
            if (it !is IdleStatus) {
//                _state.value = WorkingState(it)
            }
        }.launchIn(viewModelScope)
    }

    private fun requestBluetoothDevice() {
        navigationManager.navigateTo(ScannerDestinationId, UUIDArgument(DFU_SERVICE_UUID))

        navigationManager.recentResult.onEach {
            if (it.destinationId == ScannerDestinationId) {
                handleArgs(it)
            }
        }.launchIn(viewModelScope)
    }

    private fun handleArgs(args: DestinationResult?) {
        when (args) {
            is CancelDestinationResult -> { /* do nothing */ }
            is SuccessDestinationResult -> {
                repository.device = args.getDevice()
//                _state.value = FileSummaryState(repository.zipFile!!, repository.device!!)
            }
            null -> navigationManager.navigateTo(ScannerDestinationId)
        }.exhaustive
    }

    fun onEvent(event: DFUViewEvent) {
        when (event) {
            OnDisconnectButtonClick -> navigationManager.navigateUp()
            OnInstallButtonClick -> repository.launch(viewModelScope)
            OnAbortButtonClick -> repository.abort()
            is OnZipFileSelected -> onZipFileSelected(event.file)
            NavigateUp -> navigationManager.navigateUp()
            OnCloseButtonClick -> navigationManager.navigateUp()
        }.exhaustive
    }

    private fun onZipFileSelected(uri: Uri) {
        val zipFile = repository.setZipFile(uri)
        if (zipFile == null) {
            _state.value = _state.value.copy(fileViewEntity = NotSelectedFileViewEntity(true))
        } else {
            _state.value = _state.value.copy(fileViewEntity = SelectedFileViewEntity(zipFile))
//            requestBluetoothDevice()
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.release()
    }
}
