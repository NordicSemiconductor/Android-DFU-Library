package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanFilter;
import android.os.Build;
import android.os.ParcelUuid;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

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
	 * Returns a list of scan filters that should be used to filter scan results when scanning for
	 * the device advertising in bootloader mode.
	 * <p>
	 * Scan filters are required since around Android 12 for background scanning, but they also
	 * increase the time it takes to find the device.
	 * Remember to set {@link DfuServiceInitiator#setScanTimeout(long)} to at least 5 seconds.
	 *
	 * @param dfuServiceUuid The UUID of the DFU service in use. If the UUID was altered using
	 *                       {@link UuidHelper}, this will contain the new UUID.
	 * @return list of scan filters to use.
	 */
	@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    default List<ScanFilter> getScanFilters(final @NonNull ParcelUuid dfuServiceUuid) {
		final List<ScanFilter> filters = new ArrayList<>();
		filters.add(new ScanFilter.Builder().setServiceUuid(dfuServiceUuid).build());
		filters.add(new ScanFilter.Builder().build()); // All devices
		return filters;
	}

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
			final @NonNull BluetoothDevice device,
			final int rssi,
			final @NonNull byte[] scanRecord,
			final @NonNull String originalAddress,
			final @NonNull String incrementedAddress
	);
}