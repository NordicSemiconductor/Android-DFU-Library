package no.nordicsemi.android.dfu.scanner;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;

/**
 * @see BootloaderScanner
 */
public class BootloaderScannerJB implements BootloaderScanner, BluetoothAdapter.LeScanCallback {
	private Object mLock = new Object();
	private String mDeviceAddress;
	private String mDeviceAddressIncremented;
	private String mBootloaderAddress;
	private boolean mFound;

	@SuppressWarnings("deprecation")
	@Override
	public String searchFor(final String deviceAddress) {
		final String fistBytes = deviceAddress.substring(0, 15);
		final String lastByte = deviceAddress.substring(15); // assuming that the device address is correct
		final String lastByteIncremented = String.format("%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);

		mDeviceAddress = deviceAddress;
		mDeviceAddressIncremented = fistBytes + lastByteIncremented;
		mBootloaderAddress = null;
		mFound = false;

		// Add timeout
		new Handler().postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mFound)
					return;

				mBootloaderAddress = null;
				mFound = true;

				// Notify the waiting thread
				synchronized (mLock) {
					mLock.notifyAll();
				}
			}
		}, BootloaderScanner.TIMEOUT);

		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		adapter.startLeScan(this);

		try {
			synchronized (mLock) {
				while (!mFound)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			// do nothing
		}

		adapter.stopLeScan(this);
		return mBootloaderAddress;
	}

	@Override
	public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
		final String address = device.getAddress();
		if (mDeviceAddress.equals(address) || mDeviceAddressIncremented.equals(address)) {
			mBootloaderAddress = address;
			mFound = true;

			// Notify the waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	}

}
