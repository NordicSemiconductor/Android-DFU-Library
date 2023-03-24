/*
 * Copyright (c) 2018, Nordic Semiconductor
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
 */

package no.nordicsemi.android.dfu.internal.scanner;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import no.nordicsemi.android.dfu.DfuDeviceSelector;

/**
 * @see BootloaderScanner
 */
@SuppressLint("MissingPermission")
public class BootloaderScannerJB implements BootloaderScanner, BluetoothAdapter.LeScanCallback {
	private final Object mLock = new Object();
	private final String mDeviceAddress;
	private final String mDeviceAddressIncremented;
	private DfuDeviceSelector mSelector;
	private String mBootloaderAddress;
	private boolean mFound;

	BootloaderScannerJB(final String deviceAddress, final String deviceAddressIncremented) {
		mDeviceAddress = deviceAddress;
		mDeviceAddressIncremented = deviceAddressIncremented;
	}

	@Nullable
	@Override
	public String searchUsing(@NonNull DfuDeviceSelector selector, long timeout) {
		mSelector = selector;
		mBootloaderAddress = null;
		mFound = false;

		// Add timeout
		new Thread(() -> {
			try {
				Thread.sleep(timeout);
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
		}, "Scanner timer").start();

		final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
		if (adapter == null || adapter.getState() != BluetoothAdapter.STATE_ON)
			return null;
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
	public void onLeScan(@NonNull final BluetoothDevice device, final int rssi, final byte[] scanRecord) {
		final String address = device.getAddress();

		if (!mFound && mSelector.matches(
				device, rssi,
				scanRecord,
				mDeviceAddress, mDeviceAddressIncremented
		)) {
			mBootloaderAddress = address;
			mFound = true;

			// Notify the waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	}

}
