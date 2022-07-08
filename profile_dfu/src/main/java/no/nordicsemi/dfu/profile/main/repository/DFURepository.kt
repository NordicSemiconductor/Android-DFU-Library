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
