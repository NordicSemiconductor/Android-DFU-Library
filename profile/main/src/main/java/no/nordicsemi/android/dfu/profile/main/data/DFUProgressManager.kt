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
import kotlinx.coroutines.flow.MutableStateFlow
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.android.dfu.profile.main.R
import no.nordicsemi.android.error.SecureDfuError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DFUProgressManager @Inject constructor(
    @ApplicationContext private val context: Context
) : DfuProgressListenerAdapter() {
    val status = MutableStateFlow<DfuState>(DfuState.Idle)

    override fun onEnablingDfuMode(deviceAddress: String) {
        status.value = DfuState.InProgress(InitializingDFU)
    }

    override fun onProgressChanged(
        deviceAddress: String,
        percent: Int,
        speed: Float,
        avgSpeed: Float,
        currentPart: Int,
        partsTotal: Int
    ) {
        status.value = DfuState.InProgress(Uploading(percent, avgSpeed, currentPart, partsTotal))
    }

    override fun onDfuCompleted(deviceAddress: String) {
        status.value = DfuState.InProgress(Completed)
    }

    override fun onDfuAborted(deviceAddress: String) {
        status.value = DfuState.InProgress(Aborted)
    }

    fun onFileError() {
        status.value = DfuState.InProgress(InvalidFile)
    }

    override fun onError(
        deviceAddress: String,
        error: Int,
        errorType: Int,
        message: String?
    ) {
        val betterMessage = when (error) {
            DfuBaseService.ERROR_DEVICE_DISCONNECTED -> context.getString(R.string.dfu_error_link_loss)
            DfuBaseService.ERROR_FILE_ERROR -> context.getString(R.string.dfu_error_file_error)
            DfuBaseService.ERROR_FILE_INVALID -> context.getString(R.string.dfu_error_file_unsupported)
            DfuBaseService.ERROR_FILE_TYPE_UNSUPPORTED -> context.getString(R.string.dfu_error_file_type_invalid)
            DfuBaseService.ERROR_SERVICE_NOT_FOUND -> context.getString(R.string.dfu_error_not_supported)
            DfuBaseService.ERROR_BLUETOOTH_DISABLED -> context.getString(R.string.dfu_error_bluetooth_disabled)
            DfuBaseService.ERROR_DEVICE_NOT_BONDED -> context.getString(R.string.dfu_error_not_bonded)
            DfuBaseService.ERROR_INIT_PACKET_REQUIRED -> context.getString(R.string.dfu_error_init_packet_required)
            // Secure DFU errors
            DfuBaseService.ERROR_REMOTE_TYPE_SECURE or SecureDfuError.INVALID_OBJECT -> context.getString(R.string.dfu_error_invalid_object)
            DfuBaseService.ERROR_REMOTE_TYPE_SECURE or SecureDfuError.INSUFFICIENT_RESOURCES -> context.getString(R.string.dfu_error_insufficient_resources)
            else -> message
        }
        status.value = DfuState.InProgress(Error(message!!, betterMessage))
    }

    fun registerListener() {
        DfuServiceListenerHelper.registerProgressListener(context, this)
    }

    fun unregisterListener() {
        DfuServiceListenerHelper.unregisterProgressListener(context, this)
    }

    fun start() {
        status.value = DfuState.InProgress(Starting)
    }

    fun release() {
        status.value = DfuState.Idle
    }
}
