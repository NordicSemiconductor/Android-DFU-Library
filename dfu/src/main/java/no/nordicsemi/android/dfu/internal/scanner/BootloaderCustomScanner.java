/*************************************************************************************************************************************************
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ************************************************************************************************************************************************/

package no.nordicsemi.android.dfu.internal.scanner;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.util.Log;

/**
 * @see BootloaderScanner
 */
public class BootloaderCustomScanner implements BootloaderScanner, BluetoothAdapter.LeScanCallback {
	private final Object mLock = new Object();
	private String mDeviceAddress;
	private String mBootloaderAddress;

	private String mDeviceAddressIncremented;
	private boolean mFound;
	private BootloaderReferee mReferee;

	public BootloaderCustomScanner(BootloaderReferee bootloaderReferee) {
		mReferee = bootloaderReferee;
	}

	@Override
	public String searchFor(final String deviceAddress, final String deviceName) {
		mDeviceAddress = deviceAddress;
		mDeviceAddressIncremented = getExpectedBootloaderAddress(deviceAddress);
		mBootloaderAddress = null;
		mFound = false;

		// Add timeout
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(BootloaderScanner.TIMEOUT);
				} catch (final InterruptedException e) {
					// do nothing
				}

				if (mFound)
					return;

				mBootloaderAddress = null;
				mFound = true;

				// Notify the waiting thread
				synchronized (mLock) {
					mLock.notifyAll();
				}
			}
		}, "Scanner timer").start();

		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

		final BluetoothLeScanner scanner;
		ScanCallback callback;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			scanner = adapter.getBluetoothLeScanner();
			callback = new ScanCallback() {
				@TargetApi(Build.VERSION_CODES.LOLLIPOP)
				@Override
				public void onScanResult(int callbackType, ScanResult result) {
					judge(result.getDevice(), result.getScanRecord() != null ? result.getScanRecord().getBytes(): null);
				}
			};

			/*
			 * Scanning with filters does not work on Nexus 9 (Android 5.1). No devices are found and scanner terminates on timeout.
			 * We will match the device address in the callback method instead. It's not like it should be, but at least it works.
			 */
			//final List<ScanFilter> filters = new ArrayList<>();
			//filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddress).build());
			//filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddressIncremented).build());
			final ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
			scanner.startScan(/*filters*/ null, settings, callback);
		} else {
			adapter.startLeScan(this);
			scanner = null;
			callback = null;
		}

		try {
			synchronized (mLock) {
				while (!mFound)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			// do nothing
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			scanner.stopScan(callback);
		} else {
			adapter.stopLeScan(this);
		}

		return mBootloaderAddress;
	}

	@Override
	public void onLeScan(final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
		judge(device,scanRecord);
	}

	public void judge(BluetoothDevice device, byte[] scanRecord) {
		if (isDeviceExpected(device, scanRecord)) {
			mBootloaderAddress = device.getAddress();
			mFound = true;

			// Notify the waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	}

	public boolean isDeviceExpected(BluetoothDevice device, final byte[] scanRecord) {
		if (mReferee != null) return mReferee.isDeviceExpected(device, scanRecord);
		return mDeviceAddress.equals(device.getAddress()) || mDeviceAddressIncremented.equals(device.getName());
	}

	public String getExpectedBootloaderAddress(final String originalAddress) {
		final String firstBytes = originalAddress.substring(0, 15);
		final String lastByte = originalAddress.substring(15); // assuming that the device address is correct
		final String lastByteIncremented = String.format("%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);
		return firstBytes + lastByteIncremented;
	}

	public interface BootloaderReferee {
		boolean isDeviceExpected(BluetoothDevice device, final byte[] scanRecord);
	}

}
