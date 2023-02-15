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

package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothGatt;
import android.content.Intent;

import java.io.InputStream;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ interface DfuService extends DfuCallback {

	/**
	 * This method must return true if the device is compatible with this DFU implementation,
	 * false otherwise.
     *
     * @throws DeviceDisconnectedException Thrown when the device will disconnect in the middle of
     *                                     the transmission.
     * @throws DfuException                Thrown if DFU error occur.
     * @throws UploadAbortedException      Thrown if DFU operation was aborted by user.
	 */
	boolean isClientCompatible(@NonNull final Intent intent, @NonNull final BluetoothGatt gatt)
			throws DfuException, DeviceDisconnectedException, UploadAbortedException;

	/**
	 * Initializes the DFU implementation and does some initial setting up.
	 *
	 * @return True if initialization was successful and the DFU process may begin,
	 * false to finish teh DFU service.
	 * @throws DeviceDisconnectedException Thrown when the device will disconnect in the middle of
	 *                                     the transmission.
	 * @throws DfuException                Thrown if DFU error occur.
	 * @throws UploadAbortedException      Thrown if DFU operation was aborted by user.
	 */
	boolean initialize(@NonNull final Intent intent, @NonNull final BluetoothGatt gatt,
					   @FileType final int fileType,
					   @NonNull final InputStream firmwareStream,
                       @Nullable final InputStream initPacketStream)
			throws DfuException, DeviceDisconnectedException, UploadAbortedException;

	/**
	 * Performs the DFU process.
	 *
	 * @throws DeviceDisconnectedException Thrown when the device will disconnect in the middle of
	 *                                     the transmission.
	 * @throws DfuException                Thrown if DFU error occur.
	 * @throws UploadAbortedException      Thrown if DFU operation was aborted by user.
	 */
	void performDfu(@NonNull final Intent intent)
			throws DfuException, DeviceDisconnectedException, UploadAbortedException;

	/**
	 * Releases the service.
	 */
	void release();
}
