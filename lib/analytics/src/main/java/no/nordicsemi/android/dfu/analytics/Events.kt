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

package no.nordicsemi.android.dfu.analytics

import android.os.Bundle
import androidx.core.os.bundleOf

private object  FirebaseParam {
    const val MESSAGE = "message"
    const val IS_ENABLED = "is_enabled"
    const val VALUE = "value"
    const val SIZE_BYTES = "size_in_bytes"
}

sealed interface DfuEvent {
    val eventName: String
}

object AppOpenEvent : DfuEvent {
    override val eventName: String = "APP_OPEN_EVENT"
}

class FileSelectedEvent(
    private val fileSize: Long
) : DfuEvent {
    override val eventName: String = "FILE_SELECTED"

    fun createBundle() =
        bundleOf(FirebaseParam.SIZE_BYTES to fileSize)
}

object InstallationStartedEvent : DfuEvent {
    override val eventName: String = "INSTALLATION_STARTED"
}

object HandleDeepLinkEvent : DfuEvent {
    override val eventName: String = "HANDLE_DEEP_LINK_EVENT"
}

object ResetSettingsEvent: DfuEvent {
    override val eventName: String = "SETTINGS_RESET"
}

sealed interface DFUResultEvent : DfuEvent

object DFUSuccessEvent : DFUResultEvent {
    override val eventName: String = "DFU_SUCCESS_RESULT"
}

object DFUAbortedEvent : DFUResultEvent {
    override val eventName: String = "DFU_ABORTED_RESULT"
}

class DFUErrorEvent(
    private val errorMessage: String
) : DFUResultEvent {
    override val eventName: String = "DFU_ERROR_RESULT"

    fun createBundle() = bundleOf(FirebaseParam.MESSAGE to errorMessage)
}

sealed interface DFUSettingsChangeEvent : DfuEvent {
    fun createBundle(): Bundle
}

class PacketsReceiptNotificationSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "PACKETS_RECEIPT_CHANGE_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.IS_ENABLED to isEnabled)
}

class NumberOfPacketsSettingsEvent(private val numberOfPackets: Int) : DFUSettingsChangeEvent {
    override val eventName: String = "NUMBER_OF_PACKETS_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.VALUE to numberOfPackets)
}

class PrepareDataObjectDelaySettingsEvent(private val delay: Int) : DFUSettingsChangeEvent {
    override val eventName: String = "PREPARE_DATA_OBJECT_DELAY_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.VALUE to delay)
}

class RebootTimeSettingsEvent(private val rebootTime: Int) : DFUSettingsChangeEvent {
    override val eventName: String = "REBOOT_TIME_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.VALUE to rebootTime)
}

class ScanTimeoutSettingsEvent(private val scanTimeout: Int) : DFUSettingsChangeEvent {
    override val eventName: String = "SCAN_TIMEOUT_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.VALUE to scanTimeout)
}

class KeepBondSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "KEEP_BOND_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.IS_ENABLED to isEnabled)
}

class ExternalMCUSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "EXTERNAL_MCU_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.IS_ENABLED to isEnabled)
}

class DisableResumeSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "DISABLE_RESUME_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.IS_ENABLED to isEnabled)
}

class ForceScanningSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "FORCE_SCANNING_EVENT"

    override fun createBundle() = bundleOf(FirebaseParam.IS_ENABLED to isEnabled)
}
