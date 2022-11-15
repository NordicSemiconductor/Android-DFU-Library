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

package no.nordicsemi.android.dfu.profile.main.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import no.nordicsemi.android.common.logger.NordicLogger
import no.nordicsemi.android.common.logger.NordicLoggerFactory
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.android.dfu.profile.main.repository.DFUService
import no.nordicsemi.android.dfu.profile.scanner.data.DfuTarget
import no.nordicsemi.android.dfu.settings.domain.DFUSettings
import javax.inject.Inject

internal class DFUManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val loggerFactory: NordicLoggerFactory,
) {
    private var logger: NordicLogger? = null

    fun install(
        file: ZipFile,
        target: DfuTarget,
        settings: DFUSettings
    ): DfuServiceController {
        logger = loggerFactory
            .create(null, target.device.address, target.name)
            .also {
                DfuServiceListenerHelper.registerLogListener(context) { _, level, message ->
                    it.log(level, message)
                }
            }

        val starter = DfuServiceInitiator(target.device.address).apply {
            setDeviceName(target.name)

            setKeepBond(settings.keepBondInformation)
            setForceDfu(settings.externalMcuDfu)

            if (settings.disableResume) {
                disableResume()
            }

            setForceScanningForNewAddressInLegacyDfu(settings.forceScanningInLegacyDfu)
            setPrepareDataObjectDelay(settings.prepareDataObjectDelay.toLong())
            setRebootTime(settings.rebootTime.toLong())
            setScanTimeout(settings.scanTimeout.toLong())
            setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)

            setPacketsReceiptNotificationsEnabled(settings.packetsReceiptNotification)
            setPacketsReceiptNotificationsValue(settings.numberOfPackets)
        }

        starter.setZip(file.uri, null)
        return starter.start(context, DFUService::class.java)
    }

    fun openLogger() {
        NordicLogger.launch(context, logger)
    }
}
