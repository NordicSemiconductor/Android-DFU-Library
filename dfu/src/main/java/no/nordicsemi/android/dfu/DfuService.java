/*************************************************************************************************************************************************
 * Copyright (c) 2016, Nordic Semiconductor
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

package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothGatt;
import android.content.Intent;

import java.io.InputStream;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ interface DfuService {
	/** Pauses the DFU process. */
	void pause();

	/** Resumes the paused DFU process. */
	void resume();

	/** Terminates the DFU process. The device will disconnect and restore old application or bootloader. */
	void abort();

	/** This method must return true if the device is compatible with this DFU implementation, false otherwise. */
	boolean hasRequiredService(final BluetoothGatt gatt);

	/** This method must return true if the device is compatible with this DFU implementation, false otherwise. */
	boolean hasRequiredCharacteristics(final BluetoothGatt gatt);

	/** Callback invoked when bond state changes to {@link android.bluetooth.BluetoothDevice#BOND_BONDED BOND_BONDED} or {@link android.bluetooth.BluetoothDevice#BOND_NONE BOND_NONE}. */
	void onBondStateChanged(final int state);

	/**
	 * Initializes the DFU implementation and does some initial setting up.
	 * @return true if initialization was successful and the DFU process may begin, false to finish teh DFU service
	 */
	boolean initialize(final Intent intent, final BluetoothGatt gatt, final int fileType, final InputStream firmwareStream, final InputStream initPacketStream) throws DfuException, DeviceDisconnectedException, UploadAbortedException;

	/** Performs the DFU process. */
	void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException;

	/** Releases the service. */
	void release();
}
