package no.nordicsemi.dfu.profile.main.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.dfu.profile.main.repository.DFUService
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import javax.inject.Inject

class DFUManager @Inject constructor(
    @ApplicationContext
    private val context: Context
) {

    fun install(
        file: ZipFile,
        device: DiscoveredBluetoothDevice,
        settings: DFUSettings
    ): DfuServiceController {

        val starter = DfuServiceInitiator(device.address()).apply {
            setDeviceName(device.displayName())

            setKeepBond(settings.keepBondInformation)
            setForceDfu(settings.externalMcuDfu)

            if (settings.disableResume) {
                disableResume()
            }

            setForceScanningForNewAddressInLegacyDfu(settings.forceScanningInLegacyDfu)
            setPrepareDataObjectDelay(400)
            setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)

            setPacketsReceiptNotificationsEnabled(settings.packetsReceiptNotification)
            setPacketsReceiptNotificationsValue(settings.numberOfPackets)
        }

        starter.setZip(file.uri, null)
        return starter.start(context, DFUService::class.java)
    }
}
