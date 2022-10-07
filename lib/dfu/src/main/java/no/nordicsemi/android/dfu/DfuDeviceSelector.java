package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;

/**
 * The device selector can be used to filter scan results when scanning for the device
 * advertising in bootloader mode.
 * <p>
 * By default, the scanner will look for a device advertising an incremented address
 * (the original address + 1). By returning a custom selector from
 * {@link DfuBaseService#getDeviceSelector()} the app can override this behavior.
 *
 * @see DfuDefaultDeviceSelector
 * @since 2.1
 */
public interface DfuDeviceSelector {

	/**
	 * This method should return true if the given device matches the expected device in bootloader
	 * mode.
	 * <p>
	 * The advertising data are given as a byte array for backwards compatibility with pre-Lollipop
	 * devices.
	 *
	 * @param device the scanned Bluetooth device.
	 * @param rssi the received signal strength indicator (RSSI) value for the device.
	 * @param scanRecord the raw scan record of the device.
	 * @param originalAddress the MAC address of the device when first connected.
	 * @param incrementedAddress the incremented address of the device.
	 * @return true if the device matches the expected device in bootloader mode.
	 * @since 2.1
	 */
	boolean matches(
			@NonNull final BluetoothDevice device,
			final int rssi,
			final @NonNull byte[] scanRecord,
			final @NonNull String originalAddress,
			final @NonNull String incrementedAddress
	);
}