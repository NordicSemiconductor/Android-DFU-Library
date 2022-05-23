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
