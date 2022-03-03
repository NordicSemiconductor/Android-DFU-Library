package no.nordicsemi.dfu.profile.settings.domain

data class DFUSettings(
    val packetsReceiptNotification: Boolean = false,
    val keepBondInformation: Boolean = false,
    val externalMcuDfu: Boolean = false
)
