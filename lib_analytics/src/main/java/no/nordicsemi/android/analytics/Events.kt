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

package no.nordicsemi.android.analytics

import android.os.Bundle

private object FirebaseParam {
    const val MESSAGE = "message"
    const val IS_ENABLED = "is_enabled"
    const val NUMBER_OF_PACKETS = "number_of_packets"
}

sealed interface AppEvent {
    val eventName: String
}

object AppOpenEvent : AppEvent {
    override val eventName: String = "APP_OPEN_EVENT"
}

object FileSelectedEvent : AppEvent {
    override val eventName: String = "FILE_SELECTED"
}

object DeviceSelectedEvent : AppEvent {
    override val eventName: String = "DEVICE_SELECTED"
}

object InstallationStartedEvent : AppEvent {
    override val eventName: String = "INSTALLATION_STARTED"
}

object HandleDeepLinkEvent : AppEvent {
    override val eventName: String = "HANDLE_DEEP_LINK_EVENT"
}

sealed interface DFUResultEvent : AppEvent

object DFUSuccessEvent : DFUResultEvent {
    override val eventName: String = "DFU_SUCCESS_RESULT"
}

class DFUErrorEvent(
    private val errorMessage: String?
) : DFUResultEvent {
    override val eventName: String = "DFU_ERROR_RESULT"

    fun createBundle(): Bundle? {
        return errorMessage?.let {
            Bundle().apply {
                putString(FirebaseParam.MESSAGE, it)
            }
        }
    }
}

sealed interface DFUSettingsChangeEvent : AppEvent {
    fun createBundle(): Bundle?
}

class PacketsReceiptNotificationSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "PACKETS_RECEIPT_CHANGE_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putBoolean(FirebaseParam.IS_ENABLED, isEnabled)
        }
    }
}

class NumberOfPacketsSettingsEvent(private val numberOfPackets: Int) : DFUSettingsChangeEvent {
    override val eventName: String = "NUMBER_OF_PACKETS_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putInt(FirebaseParam.NUMBER_OF_PACKETS, numberOfPackets)
        }
    }
}

class KeepBondSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "KEEP_BOND_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putBoolean(FirebaseParam.IS_ENABLED, isEnabled)
        }
    }
}

class ExternalMCUSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "EXTERNAL_MCU_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putBoolean(FirebaseParam.IS_ENABLED, isEnabled)
        }
    }
}

class DisableResumeSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "DISABLE_RESUME_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putBoolean(FirebaseParam.IS_ENABLED, isEnabled)
        }
    }
}

class ForceScanningSettingsEvent(private val isEnabled: Boolean) : DFUSettingsChangeEvent {
    override val eventName: String = "FORCE_SCANNING_EVENT"

    override fun createBundle(): Bundle {
        return Bundle().apply {
            putBoolean(FirebaseParam.IS_ENABLED, isEnabled)
        }
    }
}
