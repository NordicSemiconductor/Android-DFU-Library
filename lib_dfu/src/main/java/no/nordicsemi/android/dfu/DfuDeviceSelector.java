package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

public interface DfuDeviceSelector {
	boolean matches(
			@NonNull final BluetoothDevice device,
			final int rssi,
			final @NonNull byte[] scanRecord,
			final @NonNull String originalAddress,
			final @NonNull String incrementedAddress
	);
}