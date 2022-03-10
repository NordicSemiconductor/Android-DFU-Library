package no.nordicsemi.dfu.profile.main.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

internal sealed class DFUData

internal object IdleStatus : DFUData()
internal data class WorkingStatus(val status: WorkingBasedStatus) : DFUData()

internal sealed class WorkingBasedStatus

internal object Connecting : WorkingBasedStatus()
internal object Connected : WorkingBasedStatus()
internal object Starting : WorkingBasedStatus()
internal object Started : WorkingBasedStatus()
internal object EnablingDfu : WorkingBasedStatus()

@Parcelize
internal data class ProgressUpdate(
    val progress: Int = 0,
    val avgSpeed: Float = 0f,
    val currentPart: Int = 0,
    val partsTotal: Int = 0
) : WorkingBasedStatus(), Parcelable

internal object Validating : WorkingBasedStatus()
internal object Completed : WorkingBasedStatus()
internal object Aborted : WorkingBasedStatus()
internal object Disconnecting : WorkingBasedStatus()
internal object Disconnected : WorkingBasedStatus()
internal data class Error(val message: String?) : WorkingBasedStatus()
