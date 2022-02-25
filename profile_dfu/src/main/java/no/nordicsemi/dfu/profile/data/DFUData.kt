package no.nordicsemi.dfu.profile.data

internal sealed class DFUData

internal object IdleStatus : DFUData()
internal object ConnectingStatus : DFUData()
internal data class WorkingStatus(val status: WorkingBasedStatus) : DFUData()
internal object DisconnectedStatus : DFUData()

internal sealed class WorkingBasedStatus

internal object Connected : WorkingBasedStatus()
internal object Starting : WorkingBasedStatus()
internal object Started : WorkingBasedStatus()
internal object EnablingDfu : WorkingBasedStatus()
internal data class ProgressUpdate(val progress: Int) : WorkingBasedStatus()
internal object Validating : WorkingBasedStatus()
internal object Completed : WorkingBasedStatus()
internal object Aborted : WorkingBasedStatus()
internal data class Error(val message: String?) : WorkingBasedStatus()
