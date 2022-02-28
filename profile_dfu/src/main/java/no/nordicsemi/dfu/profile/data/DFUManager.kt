package no.nordicsemi.dfu.profile.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.dfu.profile.repository.DFUService
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import java.util.*
import javax.inject.Inject

val DFU_SERVICE_UUID: UUID = UUID.fromString("8EC9FE59-F315-4F60-9FB8-838830DAEA50")

class DFUManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {

    fun install(file: ZipFile, device: DiscoveredBluetoothDevice) {
        val starter = DfuServiceInitiator(device.address())
            .setDeviceName(device.displayName())
//        .setKeepBond(keepBond)
//        .setForceDfu(forceDfu)
//        .setPacketsReceiptNotificationsEnabled(enablePRNs)
//        .setPacketsReceiptNotificationsValue(numberOfPackets)
            .setPrepareDataObjectDelay(400)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)

        starter.setZip(file.uri, null)
        starter.start(context, DFUService::class.java)
    }
}
