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
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;

import java.util.UUID;

import androidx.annotation.NonNull;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/**
 * The implementation of the experimental buttonless service that was
 * implemented in SDK 12.x. The original implementation had bugs and must be enabled using this method:
 * (see {@link DfuServiceInitiator#setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(boolean)}).
 * Read this method documentation for more details.
 */
/* package */ class ExperimentalButtonlessDfuImpl extends ButtonlessDfuImpl {
	/** The UUID of the experimental Buttonless DFU service from SDK 12.x. */
	static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L);
	/** The UUID of the experimental Buttonless DFU characteristic from SDK 12.x. */
	static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID         = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L); // the same as service

	static UUID EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID;
	static UUID EXPERIMENTAL_BUTTONLESS_DFU_UUID         = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID;

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	ExperimentalButtonlessDfuImpl(@NonNull final Intent intent, @NonNull final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(@NonNull final Intent intent, @NonNull final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		final BluetoothGattCharacteristic characteristic = dfuService.getCharacteristic(EXPERIMENTAL_BUTTONLESS_DFU_UUID);
		if (characteristic == null || characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) == null)
			return false;
		mButtonlessDfuCharacteristic = characteristic;
		return true;
	}

	@Override
	protected int getResponseType() {
		return NOTIFICATIONS;
	}

	@Override
	protected BluetoothGattCharacteristic getButtonlessDfuCharacteristic() {
		return mButtonlessDfuCharacteristic;
	}

	@Override
	protected boolean shouldScanForBootloader() {
		return true;
	}

	@Override
	public void performDfu(@NonNull final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logi("Experimental buttonless service found -> SDK 12.x");
		super.performDfu(intent);
	}
}
