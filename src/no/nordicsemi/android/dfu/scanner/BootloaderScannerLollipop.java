package no.nordicsemi.android.dfu.scanner;

import java.util.ArrayList;
import java.util.List;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Handler;

/**
 * @see BootloaderScanner
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BootloaderScannerLollipop extends ScanCallback implements BootloaderScanner {
	private Object mLock = new Object();
	private String mDeviceAddress;
	private String mDeviceAddressIncremented;
	private String mBootloaderAddress;
	private boolean mFound;

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
		final BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
		final List<ScanFilter> filters = new ArrayList<ScanFilter>();
		filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddress).build());
		filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddressIncremented).build());
		final ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
		scanner.startScan(filters, settings, this);

		try {
			synchronized (mLock) {
				while (!mFound)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			// do nothing
		}

		scanner.stopScan(this);
		return mBootloaderAddress;
	}

	@Override
	public void onScanResult(final int callbackType, final ScanResult result) {
		final String address = result.getDevice().getAddress();
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
