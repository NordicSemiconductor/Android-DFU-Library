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

/**
 * <p>
 *     The DFU Bootloader may advertise with the same address as an application (in case of the buttonless update) or one incremented by 1 (in case of jumping to the DFU mode with a button,
 *     or after flashing the new Soft Device (flashing new SD removes the old application)).
 * </p>
 * <p>
 *     The DFU service always connects to the address given as a parameter. However, when flashing SD+BL+App it will first send the SD+BL as part one followed by the App in the second connection.
 *     As the service does not know which address was used in the first connection (normal, when buttonless update, or +1 when with-button update) we have to scan for the advertising device
 *     after SD+BL part is completed.
 * </p>
 */
public interface BootloaderScanner {
	/**
	 * After the buttonless jump from the application mode to the bootloader mode the service will wait this long for the advertising bootloader (in milliseconds).
	 */
	public final static long TIMEOUT = 5000l; // ms
	/** The bootloader may advertise with the same address or one with the last byte incremented by this value. F.e. 00:11:22:33:44:55 -> 00:11:22:33:44:56. FF changes to 00. */
	public final static int ADDRESS_DIFF = 1;

	/**
	 * Searches for the advertising bootloader. The bootloader may advertise with the same device address or one with the last byte incremented by 1.
	 * This method is a blocking one and ends when such device is found. There are two implementations of this interface - one for Androids 4.3 and 4.4.x and one for
	 * the Android 5+ devices.
	 *
	 * @param deviceAddress
	 *            the application device address
	 * @return the address of the advertising DFU bootloader. If may be the same as the application address or one with the last byte incremented by 1 (AA:BB:CC:DD:EE:45/FF -> AA:BB:CC:DD:EE:46/00).
	 */
	public String searchFor(final String deviceAddress);
}
