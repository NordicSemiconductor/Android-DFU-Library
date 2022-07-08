/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

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
