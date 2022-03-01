package no.nordicsemi.dfu.profile.view

internal data class DFUViewState(
    val fileViewEntity: DFUSelectFileViewEntity = NotSelectedFileViewEntity(),
    val deviceViewEntity: DFUSelectDeviceViewEntity = DisabledSelectedDeviceViewEntity,
    val progressViewEntity: DFUProgressViewEntity = DisabledProgressViewEntity
)
