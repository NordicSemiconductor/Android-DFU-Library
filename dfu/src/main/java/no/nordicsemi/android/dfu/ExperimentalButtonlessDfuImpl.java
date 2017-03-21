package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;

import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.error.SecureDfuError;

public class ExperimentalButtonlessDfuImpl extends BaseDfuImpl {
	/** The UUID of the experimental Buttonless DFU service from SDK 12.x. */
	protected static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L);
	protected static final UUID DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID         = new UUID(0x8E400001F3154F60L, 0x9FB8838830DAEA50L); // the same as service

	protected static UUID EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID;
	protected static UUID EXPERIMENTAL_BUTTONLESS_DFU_UUID         = DEFAULT_EXPERIMENTAL_BUTTONLESS_DFU_UUID;

	private static final int DFU_STATUS_SUCCESS = 1;
	private static final int ERROR_OP_CODE_NOT_SUPPORTED = 2;
	private static final int ERROR_OPERATION_FAILED = 4;

	private static final int OP_CODE_ENTER_BOOTLOADER_KEY = 0x01;
	private static final int OP_CODE_RESPONSE_CODE_KEY = 0x20;
	private static final byte[] OP_CODE_ENTER_BOOTLOADER = new byte[] {OP_CODE_ENTER_BOOTLOADER_KEY};

	private BluetoothGattCharacteristic mButtonlessDfuCharacteristic;

	private final ButtonlessBluetoothCallback mBluetoothCallback = new ButtonlessBluetoothCallback();

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

	ExperimentalButtonlessDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	protected BaseBluetoothGattCallback getGattCallback() {
		return mBluetoothCallback;
	}

	@Override
	public boolean hasRequiredService(final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID);
		return dfuService != null;
	}

	@Override
	public boolean hasRequiredCharacteristics(final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(EXPERIMENTAL_BUTTONLESS_DFU_SERVICE_UUID);
		mButtonlessDfuCharacteristic = dfuService.getCharacteristic(EXPERIMENTAL_BUTTONLESS_DFU_UUID);
		return mButtonlessDfuCharacteristic != null;
	}

	@Override
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_STARTING);

		// Add one second delay to avoid the traffic jam before the DFU mode is enabled
		// Related:
		//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
		mService.waitFor(1000);
		// End

		final BluetoothGatt gatt = mGatt;

		// The service is connected to the application, not to the bootloader
		logw("Experimental buttonless service found");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Application with buttonless update found");

		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Jumping to the DFU Bootloader...");

		// Enable notifications
		enableCCCD(mButtonlessDfuCharacteristic, NOTIFICATIONS);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Notifications enabled");

		// Wait a second here before going further
		// Related:
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
		mService.waitFor(1000);
		// End

		try {
			// Send 'enter bootloader command'
			mProgressInfo.setProgress(DfuBaseService.PROGRESS_ENABLING_DFU_MODE);
			logi("Sending Enter Bootloader (Op Code = 1)");
			writeOpCode(mButtonlessDfuCharacteristic, OP_CODE_ENTER_BOOTLOADER, true);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Enter bootloader sent (Op Code = 1)");

			byte[] response;
			try {
				// There may be a race condition here. The peripheral should send a notification and disconnect gracefully
				// immediately after that, but the onConnectionStateChange event may be handled before this method ends.
				// Also, sometimes the notification is not received at all.
				response = readNotificationResponse();
			} catch (final DeviceDisconnectedException e) {
				// The device disconnect event was handled before the method finished,
				// or the notification wasn't received. We behave as if we received status success.
				response = mReceivedData;
			}

			if (response != null) {
				/*
				 * The response received from the DFU device contains:
				 * +---------+--------+----------------------------------------------------+
				 * | byte no | value  | description                                        |
				 * +---------+--------+----------------------------------------------------+
				 * | 0       | 0x20   | Response code                                      |
				 * | 1       | 0x01   | The Op Code of a request that this response is for |
				 * | 2       | STATUS | Status code                                        |
				 * +---------+--------+----------------------------------------------------+
				 */
				final int status = getStatusCode(response, OP_CODE_ENTER_BOOTLOADER_KEY);
				logi("Response received (Op Code = " + response[1] + ", Status = " + status + ")");
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
				if (status != DFU_STATUS_SUCCESS)
					throw new RemoteDfuException("Device returned error after sending Enter Bootloader", status);
				// The device will reset so we don't have to send Disconnect signal.
				mService.waitUntilDisconnected();
			} else {
				logi("Device disconnected before receiving notification");
			}

			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

			finalize(intent, false); // Secure DFU by default preserves 3 pages of App Data, those include Att table and bond information
		} catch (final UnknownResponseException e) {
			final int error = DfuBaseService.ERROR_INVALID_RESPONSE;
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, e.getMessage());
			mService.terminateConnection(gatt, error);
		} catch (final RemoteDfuException e) {
			final int error = DfuBaseService.ERROR_REMOTE_MASK | e.getErrorNumber();
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, String.format("Remote DFU error: %s", parse(error)));
			mService.terminateConnection(gatt, error);
		}
	}

	/**
	 * Closes the BLE connection to the device and removes bonding information if a proper flag was NOT set
	 * in the {@link DfuServiceInitiator#setKeepBond(boolean)}.
	 * This method will scan for a bootloader advertising with the address equal to the current or incremented by 1 and restart the service.
	 * @param intent the intent used to start the DFU service. It contains all user flags in the bundle.
	 * @param forceRefresh true, if cache should be cleared even for a bonded device. Usually the Service Changed indication should be used for this purpose.
	 */
	protected void finalize(final Intent intent, final boolean forceRefresh) {
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
		 * The experimental buttonless service from SDK 12.x and 13 does not support sharing bond information
		 * from the app to the bootloader. That means, that the DFU bootloader must advertise with advertise
		 * with address +1 and must not be paired.
		 */
		logi("Restarting to bootloader mode");
		final Intent newIntent = new Intent();
		newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
		restartService(newIntent, /* and scan for new bootloader address */ true);
	}

	/**
	 * Checks whether the response received is valid and returns the status code.
	 *
	 * @param response the response received from the DFU device.
	 * @param request  the expected Op Code
	 * @return the status code
	 * @throws UnknownResponseException if response was not valid
	 */
	private int getStatusCode(final byte[] response, final int request) throws UnknownResponseException {
		if (response == null || response.length < 3 || response[0] != OP_CODE_RESPONSE_CODE_KEY || response[1] != request ||
				(response[2] != DFU_STATUS_SUCCESS &&
						response[2] != SecureDfuError.OP_CODE_NOT_SUPPORTED &&
						response[2] != SecureDfuError.INVALID_PARAM &&
						response[2] != SecureDfuError.INSUFFICIENT_RESOURCES &&
						response[2] != SecureDfuError.INVALID_OBJECT &&
						response[2] != SecureDfuError.UNSUPPORTED_TYPE &&
						response[2] != SecureDfuError.OPERATION_FAILED &&
						response[2] != SecureDfuError.EXTENDED_ERROR))
			throw new UnknownResponseException("Invalid response received", response, OP_CODE_RESPONSE_CODE_KEY, request);
		return response[2];
	}

	private static String parse(final int error) {
		switch (error & (~DfuBaseService.ERROR_REMOTE_MASK)) {
			case ERROR_OP_CODE_NOT_SUPPORTED:	return "REMOTE DFU OP CODE NOT SUPPORTED";
			case ERROR_OPERATION_FAILED:		return "REMOTE DFU OPERATION FAILED";
			default:							return "UNKNOWN (" + error + ")";
		}
	}
}
