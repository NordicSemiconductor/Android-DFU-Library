package no.nordicsemi.dfu.profile.view

import no.nordicsemi.dfu.profile.data.DFUData
import no.nordicsemi.dfu.profile.data.ZipFile
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice

internal sealed class DFUViewState

internal data class ReadFileState(val isError: Boolean = false) : DFUViewState()

internal data class FileSummaryState(
    val file: ZipFile,
    val device: DiscoveredBluetoothDevice
) : DFUViewState()

internal data class WorkingState(val result: DFUData) : DFUViewState()
