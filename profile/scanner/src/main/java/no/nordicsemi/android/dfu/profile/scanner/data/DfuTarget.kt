package no.nordicsemi.android.dfu.profile.scanner.data

import android.bluetooth.BluetoothDevice
import no.nordicsemi.android.common.ui.scanner.DeviceSelected

data class DfuTarget(
    val device: BluetoothDevice,
    val name: String?,
) {
    internal constructor(result: DeviceSelected): this(result.device.device, result.device.displayName)
}