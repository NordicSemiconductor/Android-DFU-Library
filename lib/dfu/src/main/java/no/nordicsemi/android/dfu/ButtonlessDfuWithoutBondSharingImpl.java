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
 * This implementation handles 2 services:
 * - a non-secure buttonless DFU service introduced in SDK 13
 * - a secure buttonless DFU service that will be implemented in some next SDK (14 or later)
 * <p>
 * An application that supports one of those services should have the Secure DFU Service with one
 * of those characteristic inside.
 * <p>
 * The non-secure one does not share the bond information to the bootloader, and the bootloader
 * starts advertising with address +1 after the jump. It may be used by a bonded devices
 * (it's even recommended, as it prevents from DoS attack), but the connection with the bootloader
 * will not be encrypted (in Secure DFU it is not an issue as the firmware itself is signed).
 * When implemented on a non-bonded device it is important to understand, that anyone could
 * connect to the device and switch it to DFU mode preventing the device from normal usage.
 * <p>
 * The secure one requires the device to be paired so that only the trusted phone can switch it
 * to bootloader mode. The bond information will be shared to the bootloader so it will use the
 * same device address when in DFU mode and the connection will be encrypted.
 */
/* package */ class ButtonlessDfuWithoutBondSharingImpl extends ButtonlessDfuImpl {
	/** The UUID of the Secure DFU service from SDK 12. */
	static final UUID DEFAULT_BUTTONLESS_DFU_SERVICE_UUID = SecureDfuImpl.DEFAULT_DFU_SERVICE_UUID;
	/** The UUID of the Secure Buttonless DFU characteristic without bond sharing from SDK 13. */
	static final UUID DEFAULT_BUTTONLESS_DFU_UUID = new UUID(0x8EC90003F3154F60L, 0x9FB8838830DAEA50L);

	static UUID BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_BUTTONLESS_DFU_SERVICE_UUID;
	static UUID BUTTONLESS_DFU_UUID         = DEFAULT_BUTTONLESS_DFU_UUID;

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	ButtonlessDfuWithoutBondSharingImpl(@NonNull final Intent intent, @NonNull final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(@NonNull final Intent intent, @NonNull final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(BUTTONLESS_DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		final BluetoothGattCharacteristic characteristic = dfuService.getCharacteristic(BUTTONLESS_DFU_UUID);
		if (characteristic == null || characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) == null)
			return false;
		mButtonlessDfuCharacteristic = characteristic;
		return true;
	}

	@Override
	protected int getResponseType() {
		return INDICATIONS;
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
	public void performDfu(@NonNull final Intent intent)
			throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logi("Buttonless service without bond sharing found -> SDK 13 or newer");
		if (isBonded()) {
			logw("Device is paired! Use Buttonless DFU with Bond Sharing instead (SDK 14 or newer)");
		}

		super.performDfu(intent);
	}
}
