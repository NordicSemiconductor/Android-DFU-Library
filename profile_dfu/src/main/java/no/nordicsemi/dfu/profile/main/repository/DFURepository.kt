package no.nordicsemi.dfu.profile.main.repository

import android.net.Uri
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.*
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.dfu.profile.main.data.*
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DFURepository @Inject constructor(
    private val runningObserver: DFUServiceRunningObserver,
    private val fileManger: DFUFileManager,
    private val dfuManager: DFUManager,
    private val progressManager: DFUProgressManager,
) {

    private val _data = MutableStateFlow<DFUData>(IdleStatus)
    val data: StateFlow<DFUData> = _data.asStateFlow()

    var device: DiscoveredBluetoothDevice? = null
    var zipFile: ZipFile? = null
    var dfuServiceController: DfuServiceController? = null

    fun setZipFile(file: Uri): ZipFile? {
        return fileManger.createFile(file)?.also {
            zipFile = it
        }
    }

    fun launch(settings: DFUSettings) {
        progressManager.registerListener()
        dfuServiceController = dfuManager.install(zipFile!!, device!!, settings)

        progressManager.status.onEach {
            _data.value = it
        }.launchIn(GlobalScope)
    }

    fun hasBeenInitialized(): Boolean {
        return device != null
    }

    fun abort() {
        dfuServiceController?.abort()
    }

    fun isRunning(): Boolean {
        return runningObserver.isRunning
    }

    fun openLogger() {
        dfuManager.openLogger()
    }

    fun release() {
        device = null
        zipFile = null
        dfuServiceController = null
        progressManager.release()
        progressManager.unregisterListener()
    }
}
