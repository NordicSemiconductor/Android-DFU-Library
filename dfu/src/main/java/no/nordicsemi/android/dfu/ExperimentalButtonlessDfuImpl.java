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
 * The implementation of the experimental buttonless service that was
 * implemented in SDK 12.x. The original implementation had bugs and must be enabled using this method:
 * (see {@link DfuServiceInitiator#setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(boolean)}).
 * Read this method documentation for more details.
 */
/* package */ class ExperimentalButtonlessDfuImpl extends ButtonlessDfuImpl {
	/** The UUID of the experimental Buttonless DFU service from SDK 12.x. */
	protected static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L);
	/** The UUID of the experimental Buttonless DFU characteristic from SDK 12.x. */
	protected static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID         = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L); // the same as service

	protected static UUID EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID;
	protected static UUID EXPERIMENTAL_BUTTONLESS_DFU_UUID         = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID;

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	ExperimentalButtonlessDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(final Intent intent, final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		mButtonlessDfuCharacteristic = dfuService.getCharacteristic(EXPERIMENTAL_BUTTONLESS_DFU_UUID);
		return mButtonlessDfuCharacteristic != null;
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
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logi("Experimental buttonless service found -> SDK 12.x");
		super.performDfu(intent);
	}
}
