package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;

/**
 * A base implementation of a buttonless service. The purpose of a buttonless service is to
 * switch a device into the DFU bootloader mode.
 */
/* package */ abstract class BaseButtonlessDfuImpl extends BaseDfuImpl {

	private final BaseButtonlessDfuImpl.ButtonlessBluetoothCallback mBluetoothCallback = new BaseButtonlessDfuImpl.ButtonlessBluetoothCallback();

	protected class ButtonlessBluetoothCallback extends BaseBluetoothGattCallback {
		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Notification received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
			mReceivedData = characteristic.getValue();
			notifyLock();
		}

		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			// Despite the status code the Enter bootloader request has completed.
			mRequestCompleted = true;
			notifyLock();
		}
	}

	BaseButtonlessDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public BaseBluetoothGattCallback getGattCallback() {
		return mBluetoothCallback;
	}

	/**
	 * Closes the BLE connection to the device and removes bonding information if a proper flag was NOT set
	 * in the {@link DfuServiceInitiator#setKeepBond(boolean)}.
	 * This method will scan for a bootloader advertising with the address equal to the current or incremented by 1 and restart the service.
	 * @param intent the intent used to start the DFU service. It contains all user flags in the bundle.
	 * @param forceRefresh true, if cache should be cleared even for a bonded device. Usually the Service Changed indication should be used for this purpose.
	 * @param scanForBootloader true to scan for advertising bootloader, false to keep the same address
	 */
	protected void finalize(final Intent intent, final boolean forceRefresh, final boolean scanForBootloader) {
		/*
		 * We are done with DFU. Now the service may refresh device cache and clear stored services.
		 * For bonded device this is required only if if doesn't support Service Changed indication.
		 * Android shouldn't cache services of non-bonded devices having Service Changed characteristic in their database, but it does, so...
		 */
		final boolean keepBond = intent.getBooleanExtra(DfuBaseService.EXTRA_KEEP_BOND, false);
		mService.refreshDeviceCache(mGatt, forceRefresh || !keepBond);

		// Close the device
		mService.close(mGatt);

		/*
		 * During the update the bonding information on the target device may have been removed.
		 * To create bond with the new application set the EXTRA_RESTORE_BOND extra to true.
		 * In case the bond information is copied to the new application the new bonding is not required.
		 */
		if (mGatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
			final boolean restoreBond = intent.getBooleanExtra(DfuBaseService.EXTRA_RESTORE_BOND, false);
			if (restoreBond || !keepBond) {
				// The bond information was lost.
				removeBond();

				// Give some time for removing the bond information. 300ms was to short, let's set it to 2 seconds just to be sure.
				mService.waitFor(2000);
			}
		}

		/*
		 * The experimental buttonless service from SDK 12.x does not support sharing bond information
		 * from the app to the bootloader. That means, that the DFU bootloader must advertise with advertise
		 * with address +1 and must not be paired.
		 */
		logi("Restarting to bootloader mode");
		final Intent newIntent = new Intent();
		newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
		restartService(newIntent, scanForBootloader);
	}
}
