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

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @see BootloaderScanner
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class BootloaderScannerLollipop extends ScanCallback implements BootloaderScanner {
    private final Object mLock = new Object();
    private String mDeviceAddress;
    private String mDeviceAddressIncremented;
    private String mBootloaderAddress;
    private boolean mFound;

    @Override
    public String searchFor(final String deviceAddress) {
        final String firstBytes = deviceAddress.substring(0, 15);
        final String lastByte = deviceAddress.substring(15); // assuming that the device address is correct
        final String lastByteIncremented = String.format(Locale.US, "%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);

        mDeviceAddress = deviceAddress;
        mDeviceAddressIncremented = firstBytes + lastByteIncremented;
        mBootloaderAddress = null;
        mFound = false;

        // Add timeout
        new Thread(() -> {
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
        }, "Scanner timer").start();

        final BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || adapter.getState() != BluetoothAdapter.STATE_ON)
            return null;
        final BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner == null)
            return null;
        /*
         * Android 8.1 onwards, stops unfiltered BLE scanning on screen off. Therefore we must add a filter to
         * get scan results in case the device screen is turned off as this may affect users wanting scan/connect to the device in background.
         * See https://android.googlesource.com/platform/packages/apps/Bluetooth/+/319aeae6f4ebd13678b4f77375d1804978c4a1e1
         */
        final ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build();
        if (adapter.isOffloadedFilteringSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            final List<ScanFilter> filters = new ArrayList<>();
            filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddress).build());
            filters.add(new ScanFilter.Builder().setDeviceAddress(mDeviceAddressIncremented).build());
            scanner.startScan(filters, settings, this);
        } else {
            /*
             * Scanning with filters does not work on Nexus 9 (Android 5.1). No devices are found and scanner terminates on timeout.
             * We will match the device address in the callback method instead. It's not like it should be, but at least it works.
             */
            scanner.startScan(null, settings, this);
        }

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