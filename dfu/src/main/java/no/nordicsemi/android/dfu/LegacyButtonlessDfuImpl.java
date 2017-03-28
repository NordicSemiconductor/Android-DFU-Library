package no.nordicsemi.android.dfu;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/**
 * Implementations of the legacy buttonless service introduced in SDK 6.1.
 */
/* package */ class LegacyButtonlessDfuImpl extends BaseButtonlessDfuImpl {
	// UUIDs used by the DFU
	protected static UUID DFU_SERVICE_UUID       = LegacyDfuImpl.DEFAULT_DFU_SERVICE_UUID;
	protected static UUID DFU_CONTROL_POINT_UUID = LegacyDfuImpl.DEFAULT_DFU_CONTROL_POINT_UUID;
	protected static UUID DFU_VERSION_UUID       = LegacyDfuImpl.DEFAULT_DFU_VERSION_UUID;

	private static final byte[] OP_CODE_ENTER_BOOTLOADER = new byte[] {0x01, 0x04};

	private BluetoothGattCharacteristic mControlPointCharacteristic;
	private int mVersion;

	LegacyButtonlessDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(final Intent intent, final BluetoothGatt gatt) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		mControlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
		if (mControlPointCharacteristic == null)
			return false;

		mProgressInfo.setProgress(DfuBaseService.PROGRESS_STARTING);

		// Add one second delay to avoid the traffic jam before the DFU mode is enabled
		// Related:
		//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
		mService.waitFor(1000);
		// End

		/*
		 * Read the version number if available.
		 * The DFU Version characteristic has been added in SDK 7.0.
		 * The version number consists of 2 bytes: major and minor. Therefore e.g. the version 5 (00-05) can be read as 0.5.
		 *
		 * Currently supported versions are:
		 *  * no DFU Version characteristic - we may be either in the bootloader mode or in the app mode. The DFU Bootloader from SDK 6.1 did not have this characteristic,
		 *                                    but it also supported the buttonless update. Usually, the application must have had some additional services (like Heart Rate, etc)
		 *                                    so if the number of services greater is than 3 (Generic Access, Generic Attribute, DFU Service) we can also assume we are in
		 *                                    the application mode and jump is required.
		 *
		 *  * version = 1 (major = 0, minor = 1) - Application with DFU buttonless update supported. A jump to DFU mode is required.
		 *
		 *  * version = 5 (major = 0, minor = 5) - Since version 5 the Extended Init Packet is required. Keep in mind that if we are in the app mode the DFU Version characteristic
		 *  								  still returns version = 1, as it is independent from the DFU Bootloader. The version = 5 is reported only after successful jump to
		 *  								  the DFU mode. In version = 5 the bond information is always lost. Released in SDK 7.0.0.
		 *
		 *  * version = 6 (major = 0, minor = 6) - The DFU Bootloader may be configured to keep the bond information after application update. Please, see the {@link #EXTRA_KEEP_BOND}
		 *  								  documentation for more information about how to enable the feature (disabled by default). A change in the DFU bootloader source and
		 *  								  setting the {@link DfuServiceInitiator#setKeepBond} to true is required. Released in SDK 8.0.0.
		 *
		 *  * version = 7 (major = 0, minor = 7) - The SHA-256 firmware hash is used in the Extended Init Packet instead of CRC-16. This feature is transparent for the DFU Service.
		 *
		 *  * version = 8 (major = 0, minor = 8) - The Extended Init Packet is signed using the private key. The bootloader, using the public key, is able to verify the content.
		 *  								  Released in SDK 9.0.0 as experimental feature.
		 *  								  Caution! The firmware type (Application, Bootloader, SoftDevice or SoftDevice+Bootloader) is not encrypted as it is not a part of the
		 *  								  Extended Init Packet. Use Secure DFU instead for better security.
		 */
		int version = 0;
		final BluetoothGattCharacteristic versionCharacteristic = dfuService.getCharacteristic(DFU_VERSION_UUID); // this may be null for older versions of the Bootloader
		if (versionCharacteristic != null) {
			version = mVersion = readVersion(gatt, versionCharacteristic);
			final int minor = (version & 0x0F);
			final int major = (version >> 8);
			logi("Version number read: " + major + "." + minor + " -> " + getVersionFeatures(version));
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Version number read: " + major + "." + minor);
		} else {
			logi("No DFU Version characteristic found -> " + getVersionFeatures(version));
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "DFU Version characteristic not found");
		}

		/*
		 * In case of old DFU bootloader versions, where there was no DFU Version characteristic, the service was unable to determine whether it was in the application mode, or in
		 * bootloader mode. In that case, if the following boolean value is set to false (default) the bootloader will count number of services on the device. In case of 3 service
		 * it will start the DFU procedure (Generic Access, Generic Attribute, DFU Service). If more services will be found, it assumes that a jump to the DFU bootloader is required.
		 *
		 * However, in some cases, the DFU bootloader is used to flash firmware on other chip via nRF5x. In that case the application may support DFU operation without switching
		 * to the bootloader mode itself.
		 *
		 * For newer implementations of DFU in such case the DFU Version should return value other than 0x0100 (major 0, minor 1) which means that the application does not support
		 * DFU process itself but rather support jump to the bootloader mode.
		 */
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mService);
		boolean assumeDfuMode = preferences.getBoolean(DfuSettingsConstants.SETTINGS_ASSUME_DFU_NODE, false);
		if (intent.hasExtra(DfuBaseService.EXTRA_FORCE_DFU))
			assumeDfuMode = intent.getBooleanExtra(DfuBaseService.EXTRA_FORCE_DFU, false);

		final boolean moreServicesFound = gatt.getServices().size() > 3; // More services than Generic Access, Generic Attribute, DFU Service
		if (version == 0 && moreServicesFound)
			logi("Additional services found -> Bootloader from SDK 6.1. Updating SD and BL supported, extended init packet not supported");
		return version == 1 || (!assumeDfuMode && version == 0 && moreServicesFound);
	}

	@Override
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logw("Application with legacy buttonless update found");

		// The service is connected to the application, not to the bootloader
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Application with buttonless update found");

		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Jumping to the DFU Bootloader...");

		// Enable notifications
		enableCCCD(mControlPointCharacteristic, NOTIFICATIONS);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Notifications enabled");

		// Wait a second here before going further
		// Related:
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
		mService.waitFor(1000);
		// End

		// Send 'jump to bootloader command' (Start DFU)
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_ENABLING_DFU_MODE);
		logi("Sending Start DFU command (Op Code = 1, Upload Mode = 4)");
		writeOpCode(mControlPointCharacteristic, OP_CODE_ENTER_BOOTLOADER, true);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Jump to bootloader sent (Op Code = 1, Upload Mode = 4)");

		// The device will reset so we don't have to send Disconnect signal.
		mService.waitUntilDisconnected();
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

		/*
		 * We would like to avoid using the hack with refreshing the device (refresh method is not in the public API). The refresh method clears the cached services and causes a
		 * service discovery afterwards (when connected). Android, however, does it itself when receive the Service Changed indication when bonded.
		 * In case of unpaired device we may either refresh the services manually (using the hack), or include the Service Changed characteristic.
		 *
		 * According to Bluetooth Core 4.0 (and 4.1) specification:
		 *
		 * [Vol. 3, Part G, 2.5.2 - Attribute Caching]
		 * Note: Clients without a trusted relationship must perform service discovery on each connection if the server supports the Services Changed characteristic.
		 *
		 * However, as up to Android 7 the system does NOT respect this requirement and servers are cached for every device, even if Service Changed is enabled -> Android BUG?
		 * For bonded devices Android performs service re-discovery when SC indication is received.
		 */
		final BluetoothGatt gatt = mGatt;
		final BluetoothGattService gas = gatt.getService(GENERIC_ATTRIBUTE_SERVICE_UUID);
		final boolean hasServiceChanged = gas != null && gas.getCharacteristic(SERVICE_CHANGED_UUID) != null;
		mService.refreshDeviceCache(gatt, !hasServiceChanged);

		// Close the device
		mService.close(gatt);

		logi("Starting service that will connect to the DFU bootloader");
		final Intent newIntent = new Intent();
		newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
		restartService(newIntent, /* scan only for SDK 6.1, see Pull request #45 */ mVersion == 0);
	}

	/**
	 * Reads the DFU Version characteristic if such exists. Otherwise it returns 0.
	 *
	 * @param gatt the GATT device
	 * @param characteristic the characteristic to read
	 * @return a version number or 0 if not present on the bootloader
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private int readVersion(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read version number: device disconnected");
		if (mAborted)
			throw new UploadAbortedException();
		// If the DFU Version characteristic is not available we return 0.
		if (characteristic == null)
			return 0;

		mReceivedData = null;
		mError = 0;

		logi("Reading DFU version number...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Reading DFU version number...");

		characteristic.setValue((byte[]) null);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.readCharacteristic(" + characteristic.getUuid() + ")");
		gatt.readCharacteristic(characteristic);

		// We have to wait until device receives a response or an error occur
		try {
			synchronized (mLock) {
				while (((!mRequestCompleted || characteristic.getValue() == null ) && mConnected && mError == 0 && !mAborted) || mPaused) {
					mRequestCompleted = false;
					mLock.wait();
				}
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mError != 0)
			throw new DfuException("Unable to read version number", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read version number: device disconnected");

		// The version is a 16-bit unsigned int
		return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
	}

	private String getVersionFeatures(final int version) {
		switch (version) {
			case 0:
				return "Bootloader from SDK 6.1 or older";
			case 1:
				return "Application with Legacy buttonless update from SDK 7.0 or newer";
			case 5:
				return "Bootloader from SDK 7.0 or newer. No bond sharing";
			case 6:
				return "Bootloader from SDK 8.0 or newer. Bond sharing supported";
			case 7:
				return "Bootloader from SDK 8.0 or newer. SHA-256 used instead of CRC-16 in the Init Packet";
			case 8:
				return "Bootloader from SDK 9.0 or newer. Signature supported";
			default:
				return "Unknown version";
		}
	}
}
