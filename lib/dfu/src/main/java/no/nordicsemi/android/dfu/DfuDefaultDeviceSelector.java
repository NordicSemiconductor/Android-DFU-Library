package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

/**
 * The default device selector looks for a device advertising an incremented address
 * (the original address + 1). By returning a custom selector from
 * {@link DfuBaseService#getDeviceSelector()} the app can override this behavior.
 *
 * @since 2.1
 */
class DfuDefaultDeviceSelector implements DfuDeviceSelector {
    @Override
    public boolean matches(
            @NonNull final BluetoothDevice device,
            final int rssi,
            @NonNull final byte[] scanRecord,
            @NonNull final String originalAddress,
            @NonNull final String incrementedAddress) {
        return originalAddress.equals(device.getAddress()) || incrementedAddress.equals(device.getAddress());
    }
}

