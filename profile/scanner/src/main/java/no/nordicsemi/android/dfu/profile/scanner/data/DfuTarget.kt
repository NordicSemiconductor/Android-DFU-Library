package no.nordicsemi.android.dfu.profile.scanner.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import no.nordicsemi.android.kotlin.ble.core.ServerDevice

@Parcelize
data class DfuTarget(
    val address: String,
    val name: String?,
): Parcelable {
    internal constructor(result: ServerDevice): this(result.address, result.name)
}