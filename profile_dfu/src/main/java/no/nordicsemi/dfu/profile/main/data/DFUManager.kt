package no.nordicsemi.dfu.profile.main.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.android.logger.LoggerAppRunner
import no.nordicsemi.android.logger.NordicLogger
import no.nordicsemi.android.logger.NordicLoggerFactory
import no.nordicsemi.dfu.profile.main.repository.DFUService
import no.nordicsemi.dfu.profile.settings.domain.DFUSettings
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import javax.inject.Inject

class DFUManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val appRunner: LoggerAppRunner,
    private val loggerFactory: NordicLoggerFactory
) {

    private var _logger: NordicLogger? = null

    fun install(
        file: ZipFile,
        device: DiscoveredBluetoothDevice,
        settings: DFUSettings
    ): DfuServiceController {

        val logger = loggerFactory.create("DFU", null, device.address()).also {
            _logger = it
        }
        DfuServiceListenerHelper.registerLogListener(context) { _, level, message ->
            logger.log(level, message)
        }

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

    fun openLogger() {
        val logger = _logger
        if (logger != null) {
            logger.openLogger()
        } else {
            appRunner.runLogger()
        }
    }
}
