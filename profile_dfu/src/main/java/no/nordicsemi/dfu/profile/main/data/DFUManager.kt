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

            // Legacy only
            setKeepBond(settings.keepBondInformation)
            setForceDfu(settings.externalMcuDfu)

            // Secure DFU only
            if (settings.disableResume) {
                disableResume() // zaifować
            }
//            disableMtuRequest() // wywalić albo zaifować
            setForceScanningForNewAddressInLegacyDfu(settings.forceScanningInLegacyDfu)// zaifować
            setPrepareDataObjectDelay(400) // OK
            setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true) // OK

            setPacketsReceiptNotificationsEnabled(settings.packetsReceiptNotification) // OK
            setPacketsReceiptNotificationsValue(settings.numberOfPackets) // opcja ustawienia
        }

        starter.setZip(file.uri, null)
        return starter.start(context, DFUService::class.java)
    }
}
