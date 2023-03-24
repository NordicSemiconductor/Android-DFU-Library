package no.nordicsemi.android.dfu.profile.scanner.data

import android.bluetooth.BluetoothDevice
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import no.nordicsemi.android.common.ui.scanner.DeviceSelected

@Parcelize
data class DfuTarget(
    val device: BluetoothDevice,
    val name: String?,
): Parcelable {
    internal constructor(result: DeviceSelected): this(result.device.device, result.device.displayName)
}