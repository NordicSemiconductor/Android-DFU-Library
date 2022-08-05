package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

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

