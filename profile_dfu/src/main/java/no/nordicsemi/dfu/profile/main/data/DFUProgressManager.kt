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
