package no.nordicsemi.dfu.profile.main.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DFUProgressManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) : DfuProgressListenerAdapter() {

    val status = MutableStateFlow<DFUData>(IdleStatus)

    override fun onDeviceConnecting(deviceAddress: String) {
        status.value = WorkingStatus(Connecting)
    }

    override fun onDeviceConnected(deviceAddress: String) {
        status.value = WorkingStatus(Connected)
    }

    override fun onDfuProcessStarting(deviceAddress: String) {
        status.value = WorkingStatus(Starting)
    }

    override fun onDfuProcessStarted(deviceAddress: String) {
        status.value = WorkingStatus(Started)
    }

    override fun onEnablingDfuMode(deviceAddress: String) {
        status.value = WorkingStatus(EnablingDfu)
    }

    override fun onProgressChanged(
        deviceAddress: String,
        percent: Int,
        speed: Float,
        avgSpeed: Float,
        currentPart: Int,
        partsTotal: Int
    ) {
        status.value = WorkingStatus(ProgressUpdate(percent, avgSpeed, currentPart, partsTotal))
    }

    override fun onFirmwareValidating(deviceAddress: String) {
        status.value = WorkingStatus(Validating)
    }

    override fun onDeviceDisconnecting(deviceAddress: String?) {
        status.value = WorkingStatus(Disconnecting)
    }

    override fun onDeviceDisconnected(deviceAddress: String) {
        status.value = WorkingStatus(Disconnected)
    }

    override fun onDfuCompleted(deviceAddress: String) {
        status.value = WorkingStatus(Completed)
    }

    override fun onDfuAborted(deviceAddress: String) {
        status.value = WorkingStatus(Aborted)
    }

    override fun onError(
        deviceAddress: String,
        error: Int,
        errorType: Int,
        message: String?
    ) {
        status.value = WorkingStatus(Error(message))
    }

    fun registerListener() {
        DfuServiceListenerHelper.registerProgressListener(context, this)
    }

    fun unregisterListener() {
        DfuServiceListenerHelper.unregisterProgressListener(context, this)
    }

    fun release() {
        status.value = IdleStatus
    }
}
