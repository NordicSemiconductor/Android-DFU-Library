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

import android.os.Build;

import java.util.Locale;

import androidx.annotation.NonNull;

/**
 * The factory should be used to create the {@link BootloaderScanner} instance appropriate
 * for the Android version.
 */
public final class BootloaderScannerFactory {
	/**
	 * The bootloader may advertise with the same address or one with the last byte incremented
	 * by this value. I.e. 00:11:22:33:44:55 -&gt; 00:11:22:33:44:56. FF changes to 00.
	 */
	private final static int ADDRESS_DIFF = 1;

	private BootloaderScannerFactory() {}

	public static String getIncrementedAddress(@NonNull final String deviceAddress) {
		final String firstBytes = deviceAddress.substring(0, 15);
		final String lastByte = deviceAddress.substring(15); // assuming that the device address is correct
		final String lastByteIncremented = String.format(Locale.US, "%02X", (Integer.valueOf(lastByte, 16) + ADDRESS_DIFF) & 0xFF);
		return firstBytes + lastByteIncremented;
	}

	/**
	 * Returns the scanner implementation.
	 *
	 * @return the bootloader scanner
	 */
	public static BootloaderScanner getScanner(@NonNull final String deviceAddress) {
		final String deviceAddressIncremented = getIncrementedAddress(deviceAddress);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return new BootloaderScannerLollipop(deviceAddress, deviceAddressIncremented);
		return new BootloaderScannerJB(deviceAddress, deviceAddressIncremented);
	}
}
