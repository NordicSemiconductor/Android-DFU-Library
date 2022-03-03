package no.nordicsemi.dfu.profile.main.data

internal sealed class DFUData

internal object IdleStatus : DFUData()
internal data class WorkingStatus(val status: WorkingBasedStatus) : DFUData()

internal sealed class WorkingBasedStatus

internal object Connecting : WorkingBasedStatus()
internal object Connected : WorkingBasedStatus()
internal object Starting : WorkingBasedStatus()
internal object Started : WorkingBasedStatus()
internal object EnablingDfu : WorkingBasedStatus()
internal data class ProgressUpdate(val progress: Int) : WorkingBasedStatus()
internal object Validating : WorkingBasedStatus()
internal object Completed : WorkingBasedStatus()
internal object Aborted : WorkingBasedStatus()
internal object Disconnecting : WorkingBasedStatus()
internal object Disconnected : WorkingBasedStatus()
internal data class Error(val message: String?) : WorkingBasedStatus()
