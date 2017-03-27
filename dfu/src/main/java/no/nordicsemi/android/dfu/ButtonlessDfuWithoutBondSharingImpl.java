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
 * This implementation handles 2 services:
 * - a non-secure buttonless DFU service introduced in SDK 13
 * - a secure buttonless DFU service that will be implemented in some next SDK (14 or later)
 *
 * An application that supports one of those services should have the Secure DFU Service with one of those characteristic inside.
 *
 * The non-secure one does not share the bond information to the bootloader, and the bootloader starts advertising with address +1 after the jump.
 * It may be used by a bonded devices (it's even recommended, as it prevents from DoS attack), but the connection with the bootloader will not
 * be encrypted (in Secure DFU it is not an issue as the firmware itself is signed). When implemented on a non-bonded device it is
 * important to understand, that anyone could connect to the device and switch it to DFU mode preventing the device from normal usage.
 *
 * The secure one requires the device to be paired so that only the trusted phone can switch it to bootloader mode.
 * The bond information will be shared to the bootloader so it will use the same device address when in DFU mode and
 * the connection will be encrypted.
 */
/* package */ class ButtonlessDfuWithoutBondSharingImpl extends ButtonlessDfuImpl {
	/** The UUID of the Secure DFU service from SDK 12. */
	protected static final UUID DEFAULT_BUTTONLESS_DFU_SERVICE_UUID = SecureDfuImpl.DEFAULT_DFU_SERVICE_UUID;
	/** The UUID of the Secure Buttonless DFU characteristic without bond sharing from SDK 13. */
	protected static final UUID DEFAULT_BUTTONLESS_DFU_UUID = new UUID(0x8EC90003F3154F60L, 0x9FB8838830DAEA50L);

	protected static UUID BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_BUTTONLESS_DFU_SERVICE_UUID;
	protected static UUID BUTTONLESS_DFU_UUID         = DEFAULT_BUTTONLESS_DFU_UUID;

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	ButtonlessDfuWithoutBondSharingImpl(final Intent intent, final DfuBaseService service) {
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
		return true;
	}

	@Override
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logi("Buttonless service without bond sharing found -> SDK 13 or newer");
		super.performDfu(intent);
	}
}
