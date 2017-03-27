package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;

import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/**
 * This implementation handles the secure buttonless DFU service that will be implemented in SDK 14 or later.
 *
 * This service requires the device to be paired, so that only a trusted phone can switch it to bootloader mode.
 * The bond information will be shared to the bootloader and it will use the same device address when in DFU mode and
 * the connection will be encrypted.
 */
/* package */ class ButtonlessDfuWithBondSharingImpl extends ButtonlessDfuImpl {
	/** The UUID of the Secure DFU service from SDK 12. */
	protected static final UUID DEFAULT_BUTTONLESS_DFU_SERVICE_UUID = SecureDfuImpl.DEFAULT_DFU_SERVICE_UUID;
	/** The UUID of the Secure Buttonless DFU characteristic with bond sharing from SDK 14 or later (not released yet). */
	protected static final UUID DEFAULT_BUTTONLESS_DFU_UUID = new UUID(0x8EC90004F3154F60L, 0x9FB8838830DAEA50L);

	protected static UUID BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_BUTTONLESS_DFU_SERVICE_UUID;
	protected static UUID BUTTONLESS_DFU_UUID         = DEFAULT_BUTTONLESS_DFU_UUID;

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	ButtonlessDfuWithBondSharingImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(final Intent intent, final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(BUTTONLESS_DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		mButtonlessDfuCharacteristic = dfuService.getCharacteristic(BUTTONLESS_DFU_UUID);
		return mButtonlessDfuCharacteristic != null;
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
		return false;
	}

	@Override
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logi("Buttonless service with bond sharing found -> SDK 14 or newer");
		if (!isBonded()) {
			logw("Device is not paired, cancelling DFU");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Device is not bonded");
			mService.terminateConnection(mGatt, DfuBaseService.ERROR_DEVICE_NOT_BONDED);
			return;
		}
		super.performDfu(intent);
	}
}
