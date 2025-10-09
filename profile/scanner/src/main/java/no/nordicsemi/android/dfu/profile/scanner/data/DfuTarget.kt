package no.nordicsemi.android.dfu.profile.scanner.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import no.nordicsemi.kotlin.ble.client.android.Peripheral

@Parcelize
data class DfuTarget(
    val address: String,
    val name: String?,
): Parcelable {
    internal constructor(result: Peripheral, name: String?): this(result.address, name)
}