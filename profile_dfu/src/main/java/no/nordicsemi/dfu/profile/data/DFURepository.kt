package no.nordicsemi.dfu.profile.data

import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import no.nordicsemi.dfu.profile.repository.DFUService
import no.nordicsemi.dfu.profile.repository.ServiceManager
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DFURepository @Inject constructor(
    private val fileManger: DFUFileManager,
    private val dfuManager: DFUManager,
    private val progressManager: DFUProgressManager,
    private val serviceManager: ServiceManager,
) {

    private val _data = MutableStateFlow<DFUData>(IdleStatus)
    val data: StateFlow<DFUData> = _data.asStateFlow()

    var device: DiscoveredBluetoothDevice? = null
    var zipFile: ZipFile? = null

    fun setZipFile(file: Uri): ZipFile? {
        return fileManger.createFile(file)?.also {
            zipFile = it
        }
    }

    fun launch(scope: CoroutineScope) {
        progressManager.registerListener()
        dfuManager.install(zipFile!!, device!!)

        progressManager.status.onEach {
            _data.value = it
        }.launchIn(scope)
    }

    fun hasBeenInitialized(): Boolean {
        return device != null
    }

    fun pause() {
        TODO()
    }

    fun stop() {
        TODO()
    }

    fun release() {
        progressManager.unregisterListener()
    }
}
