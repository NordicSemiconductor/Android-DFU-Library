package no.nordicsemi.dfu.profile.view

internal data class DFUViewState(
    val fileViewEntity: DFUSelectFileViewEntity = NotSelectedFileViewEntity(),
    val deviceViewEntity: DFUSelectDeviceViewEntity = DisabledSelectedDeviceViewEntity,
    val progressViewEntity: DFUProgressViewEntity = DisabledProgressViewEntity
) {
    fun isRunning(): Boolean {
        return (progressViewEntity as? WorkingProgressViewEntity)?.status?.isRunning() == true
    }

    fun isCompleted(): Boolean {
        return (progressViewEntity as? WorkingProgressViewEntity)?.status?.isCompleted() == true
    }
}
