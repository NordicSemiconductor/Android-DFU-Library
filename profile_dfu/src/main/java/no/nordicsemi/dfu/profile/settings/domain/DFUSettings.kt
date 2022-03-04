package no.nordicsemi.dfu.profile.settings.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

internal const val NUMBER_OF_POCKETS_INITIAL = 12

@Parcelize
data class DFUSettings(
    val packetsReceiptNotification: Boolean = false,
    val numberOfPackets: Int = NUMBER_OF_POCKETS_INITIAL,
    val keepBondInformation: Boolean = false,
    val externalMcuDfu: Boolean = false,
    val disableResume: Boolean = false,
    val forceScanningInLegacyDfu: Boolean = false,
    val showWelcomeScreen: Boolean = true
) : Parcelable
