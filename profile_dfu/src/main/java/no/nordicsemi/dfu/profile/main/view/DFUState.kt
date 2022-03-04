package no.nordicsemi.dfu.profile.main.view

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings

@Parcelize
internal data class DFUViewState(
    val fileViewEntity: DFUSelectFileViewEntity = NotSelectedFileViewEntity(),
    val deviceViewEntity: DFUSelectDeviceViewEntity = DisabledSelectedDeviceViewEntity,
    val progressViewEntity: DFUProgressViewEntity = DisabledProgressViewEntity,
    val settings: DFUSettings? = null
): Parcelable {
    fun isRunning(): Boolean {
        return (progressViewEntity as? WorkingProgressViewEntity)?.status?.isRunning() == true
    }

    fun isCompleted(): Boolean {
        return (progressViewEntity as? WorkingProgressViewEntity)?.status?.isCompleted() == true
    }
}
