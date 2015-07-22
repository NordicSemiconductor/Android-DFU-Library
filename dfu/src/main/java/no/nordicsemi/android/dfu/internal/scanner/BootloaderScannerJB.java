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

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

/**
 * @see BootloaderScanner
 */
public class BootloaderScannerJB implements BootloaderScanner, BluetoothAdapter.LeScanCallback {
	private final Object mLock = new Object();
	private String mDeviceAddress;
	private String mDeviceAddressIncremented;
	private String mBootloaderAddress;
	private boolean mFound;

	@SuppressWarnings("deprecation")
	@Override
	public String searchFor(final String deviceAddress) {
		final String firstBytes = deviceAddress.substring(0, 15);
		final String lastByte = deviceAddress.substring(15); // assuming that the device address is correct
		final String lastByteIncremented = String.format("%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);

		mDeviceAddress = deviceAddress;
		mDeviceAddressIncremented = firstBytes + lastByteIncremented;
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
