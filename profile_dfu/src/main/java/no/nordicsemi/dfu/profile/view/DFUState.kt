package no.nordicsemi.dfu.profile.view

import no.nordicsemi.dfu.profile.view.components.DFUSelectDeviceViewEntity
import no.nordicsemi.dfu.profile.view.components.DFUSelectFileViewEntity
import no.nordicsemi.dfu.profile.view.components.NotSelectedFileViewEntity
import no.nordicsemi.dfu.profile.view.components.NotSelectedDeviceViewEntity

internal data class DFUViewState(
    val fileViewEntity: DFUSelectFileViewEntity = NotSelectedFileViewEntity(),
    val deviceViewEntity: DFUSelectDeviceViewEntity = NotSelectedDeviceViewEntity
)

//internal data class ReadFileState(val isError: Boolean = false) : DFUViewState()
//
//internal data class FileSummaryState(
//    val file: ZipFile,
//    val device: DiscoveredBluetoothDevice
//) : DFUViewState()
//
//internal data class WorkingState(val result: DFUData) : DFUViewState()
