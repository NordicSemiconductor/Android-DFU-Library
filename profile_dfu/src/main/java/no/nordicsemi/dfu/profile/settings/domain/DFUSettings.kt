package no.nordicsemi.dfu.profile.settings.domain

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class DFUSettings(
    val packetsReceiptNotification: Boolean = false,
    val keepBondInformation: Boolean = false,
    val externalMcuDfu: Boolean = false,
    val showWelcomeScreen: Boolean = true
) : Parcelable
