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

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ abstract class BaseDfuImpl implements DfuService {
	private static final String TAG = "DfuImpl";

	protected static final UUID GENERIC_ATTRIBUTE_SERVICE_UUID = new UUID(0x0000180100001000L, 0x800000805F9B34FBL);
	protected static final UUID SERVICE_CHANGED_UUID = new UUID(0x00002A0500001000L, 0x800000805F9B34FBL);
	protected static final UUID CLIENT_CHARACTERISTIC_CONFIG = new UUID(0x0000290200001000L, 0x800000805f9b34fbL);
	protected static final int NOTIFICATIONS = 1;
	protected static final int INDICATIONS = 2;

	protected static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	protected static final int MAX_PACKET_SIZE = 20; // the maximum number of bytes in one packet is 20. May be less.

	/**
	 * Lock used in synchronization purposes
	 */
	protected final Object mLock = new Object();

	protected InputStream mFirmwareStream;
	protected InputStream mInitPacketStream;

	/** The target GATT device. */
	protected BluetoothGatt mGatt;
	/** The firmware type. See TYPE_* constants. */
	protected int mFileType;
	/** Flag set to true if sending was paused. */
	protected boolean mPaused;
	/** Flag set to true if sending was aborted. */
	protected boolean mAborted;
	/** Flag indicating whether the device is still connected. */
	protected boolean mConnected;
	/**
	 * Flag indicating whether the request was completed or not
	 */
	protected boolean mRequestCompleted;
	/**
	 * Flag sent when a request has been sent that will cause the DFU target to reset. Often, after sending such command, Android throws a connection state error. If this flag is set the error will be
	 * ignored.
	 */
	protected boolean mResetRequestSent;
	/**
	 * The number of the last error that has occurred or 0 if there was no error
	 */
	protected int mError;
	/**
	 * Latest data received from device using notification.
	 */
	protected byte[] mReceivedData = null;
	protected final byte[] mBuffer = new byte[MAX_PACKET_SIZE];
	protected DfuBaseService mService;
	protected DfuProgressInfo mProgressInfo;
	protected int mImageSizeInBytes;
	protected int mInitPacketSizeInBytes;

	protected class BaseBluetoothGattCallback extends BluetoothGattCallback {
		// The Implementation object is created depending on device services, so after the device is connected and services were scanned.
		// public void onConnected() { }

		public void onDisconnected() {
			mConnected = false;
			notifyLock();
		}

		@Override
		public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				/*
				 * This method is called when the DFU Version characteristic has been read.
				 */
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Read Response received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
				mReceivedData = characteristic.getValue();
				mRequestCompleted = true;
			} else {
				loge("Characteristic read error: " + status);
				mError = DfuBaseService.ERROR_CONNECTION_MASK | status;
			}
			notifyLock();
		}

		@Override
		public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Read Response received from descr." + descriptor.getCharacteristic().getUuid() + ", value (0x): " + parse(descriptor));
					if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
						// We have enabled indications for the Service Changed characteristic
						mRequestCompleted = true;
					} else {
						// reading other descriptor is not supported
						loge("Unknown descriptor read"); // this have to be implemented if needed
					}
				}
			} else {
				loge("Descriptor read error: " + status);
				mError = DfuBaseService.ERROR_CONNECTION_MASK | status;
			}
			notifyLock();
		}

		@Override
		public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Data written to descr." + descriptor.getCharacteristic().getUuid() + ", value (0x): " + parse(descriptor));
					if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
						// We have enabled indications for the Service Changed characteristic
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Indications enabled for " + descriptor.getCharacteristic().getUuid());
					} else {
						// We have enabled notifications for this characteristic
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Notifications enabled for " + descriptor.getCharacteristic().getUuid());
					}
				}
			} else {
				loge("Descriptor write error: " + status);
				mError = DfuBaseService.ERROR_CONNECTION_MASK | status;
			}
			notifyLock();
		}


		protected String parse(final BluetoothGattCharacteristic characteristic) {
			return parse(characteristic.getValue());
		}

		protected String parse(final BluetoothGattDescriptor descriptor) {
			return parse(descriptor.getValue());
		}

		private String parse(final byte[] data) {
			if (data == null)
				return "";
			final int length = data.length;
			if (length == 0)
				return "";

			final char[] out = new char[length * 3 - 1];
			for (int j = 0; j < length; j++) {
				int v = data[j] & 0xFF;
				out[j * 3] = HEX_ARRAY[v >>> 4];
				out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
				if (j != length - 1)
					out[j * 3 + 2] = '-';
			}
			return new String(out);
		}
	};

	/* package */ BaseDfuImpl(final Intent intent, final DfuBaseService service) {
		mService = service;
		mConnected = true; // the device is connected when impl object it created
	}

	@Override
	public void release() {
		mService = null;
	}

	@Override
	public void pause() {
		mPaused = true;
	}

	@Override
	public void resume() {
		mPaused = false;
		notifyLock();
	}

	@Override
	public void abort() {
		mPaused = false;
		mAborted = true;
		notifyLock();
	}

	@Override
	public void onBondStateChanged(final int state) {
		mRequestCompleted = true;
		notifyLock();
	}

	@Override
	public boolean initialize(final Intent intent, final BluetoothGatt gatt, final int fileType, final InputStream firmwareStream, final InputStream initPacketStream) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		mGatt = gatt;
		mFileType = fileType;
		mFirmwareStream = firmwareStream;
		mInitPacketStream = initPacketStream;

		final int currentPart = intent.getIntExtra(DfuBaseService.EXTRA_PART_CURRENT, 1);
		int totalParts = intent.getIntExtra(DfuBaseService.EXTRA_PARTS_TOTAL, 1);

		// Sending App together with SD or BL is not supported. It must be spilt into two parts.
		if (fileType > DfuBaseService.TYPE_APPLICATION) {
			logw("DFU target does not support (SD/BL)+App update, splitting into 2 parts");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Sending system components");
			mFileType &= ~DfuBaseService.TYPE_APPLICATION; // clear application bit
			totalParts = 2;

			// Set new content type in the ZIP Input Stream and update sizes of images
			final ArchiveInputStream zhis = (ArchiveInputStream) mFirmwareStream;
			zhis.setContentType(mFileType);
		}

		if (currentPart == 2) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Sending application");
		}

		int size;
		try {
			size = initPacketStream.available();
		} catch (final Exception e) {
			size = 0;
		}
		mInitPacketSizeInBytes = size;
		try {
			size = firmwareStream.available();
		} catch (final Exception e) {
			size = 0;
			// not possible
		}
		mImageSizeInBytes = size;
		mProgressInfo = mService.mProgressInfo.init(size, currentPart, totalParts);

		// If we are bonded we may want to enable Service Changed characteristic indications.
		// Note: This feature will be introduced in the SDK 8.0 as this is the proper way to refresh attribute list on the phone.

		// This has been fixed on Android 6 (?). Now the Android enables Service Changed indications automatically during bonding.
		if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
			final BluetoothGattService genericAttributeService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE_UUID);
			if (genericAttributeService != null) {
				final BluetoothGattCharacteristic serviceChangedCharacteristic = genericAttributeService.getCharacteristic(SERVICE_CHANGED_UUID);
				if (serviceChangedCharacteristic != null) {
					// Let's read the current value of the Service Changed CCCD
					final boolean serviceChangedIndicationsEnabled = isServiceChangedCCCDEnabled();

					if (!serviceChangedIndicationsEnabled) {
						enableCCCD(serviceChangedCharacteristic, INDICATIONS);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Service Changed indications enabled");

						/*
						 * NOTE: The DFU Bootloader from SDK 8.0 (v0.6 and 0.5) has the following issue:
						 *
						 * When the central device (phone) connects to a bonded device (or connects and bonds) which supports the Service Changed characteristic,
						 * but does not have the Service Changed indications enabled, the phone must enable them, disconnect and reconnect before starting the
						 * DFU operation. This is because the current version of the Soft Device saves the ATT table on the DISCONNECTED event.
						 * Sending the "jump to Bootloader" command (0x01-04) will cause the disconnect followed be a reset. The Soft Device does not
						 * have time to store the ATT table on Flash memory before the reset.
						 *
						 * This applies only if:
						 * - the device was bonded before an upgrade,
						 * - the Application or the Bootloader is upgraded (upgrade of the Soft Device will erase the bond information anyway),
						 *     - Application:
						  *        if the DFU Bootloader has been modified and compiled to preserve the LTK and the ATT table after application upgrade (at least 2 pages)
						 *         See: \Nordic\nrf51\components\libraries\bootloader_dfu\dfu_types.h, line 56(?):
						 *          #define DFU_APP_DATA_RESERVED           0x0000  ->  0x0800+   //< Size of Application Data that must be preserved between application updates...
						 *     - Bootloader:
						 *         The Application memory should not be removed when the Bootloader is upgraded, so the Bootloader configuration does not matter.
						 *
						 * If the bond information is not to be preserved between the old and new applications, we may skip this disconnect/reconnect process.
						 * The DFU Bootloader will send the SD indication anyway when we will just continue here, as the information whether it should send it or not it is not being
						 * read from the application's ATT table, but rather passed as an argument of the "reboot to bootloader" method.
						 */
						final boolean keepBond = intent.getBooleanExtra(DfuBaseService.EXTRA_KEEP_BOND, false);
						if (keepBond && (mFileType & DfuBaseService.TYPE_SOFT_DEVICE) == 0) {
							mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Restarting service...");

							// Disconnect
							mService.disconnect(gatt);

							// Close the device
							mService.close(gatt);

							logi("Restarting service");
							mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Restarting service...");
							final Intent newIntent = new Intent();
							newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
							mService.startService(newIntent);
							return false;
						}
					} else {
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Service Changed indications enabled");
					}
				}
			}
		}
		return true;
	}

	protected void notifyLock() {
		// Notify waiting thread
		synchronized (mLock) {
			mLock.notifyAll();
		}
	}

	protected void waitIfPaused() {
		try {
			synchronized (mLock) {
				while (mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
	}

	/**
	 * Returns the final BluetoothGattCallback instance, depending on the implementation.
	 */
	protected abstract BaseBluetoothGattCallback getGattCallback();

	/**
	 * Enables or disables the notifications for given characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onDescriptorWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattDescriptor, int)} will be called or the device gets disconnected.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param characteristic the characteristic to enable or disable notifications for
	 * @param type           {@link #NOTIFICATIONS} or {@link #INDICATIONS}
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected void enableCCCD(final BluetoothGattCharacteristic characteristic, final int type) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		final BluetoothGatt gatt = mGatt;
		final String debugString = type == NOTIFICATIONS ? "notifications" : "indications";
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to set " + debugString + " state: device disconnected");
		if (mAborted)
			throw new UploadAbortedException();

		mReceivedData = null;
		mError = 0;
		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
		boolean cccdEnabled = descriptor.getValue() != null && descriptor.getValue().length == 2 && descriptor.getValue()[0] > 0 && descriptor.getValue()[1] == 0;
		if (cccdEnabled)
			return;

		logi("Enabling " + debugString + "...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Enabling " + debugString + " for " + characteristic.getUuid());

		// enable notifications locally
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
		gatt.setCharacteristicNotification(characteristic, true);

		// enable notifications on the device
		descriptor.setValue(type == NOTIFICATIONS ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeDescriptor(" + descriptor.getUuid() + (type == NOTIFICATIONS ? ", value=0x01-00)" : ", value=0x02-00)"));
		gatt.writeDescriptor(descriptor);

		// We have to wait until device receives a response or an error occur
		try {
			synchronized (mLock) {
				while ((!cccdEnabled && mConnected && mError == 0) || mPaused) {
					mLock.wait();
					// Check the value of the CCCD
					cccdEnabled = descriptor.getValue() != null && descriptor.getValue().length == 2 && descriptor.getValue()[0] > 0 && descriptor.getValue()[1] == 0;
				}
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mError != 0)
			throw new DfuException("Unable to set " + debugString + " state", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to set " + debugString + " state: device disconnected");
	}

	/**
	 * Reads the value of the Service Changed Client Characteristic Configuration descriptor (CCCD).
	 *
	 * @return <code>true</code> if Service Changed CCCD is enabled and set to INDICATE
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private boolean isServiceChangedCCCDEnabled() throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read Service Changed CCCD: device disconnected");
		if (mAborted)
			throw new UploadAbortedException();

		// If the Service Changed characteristic or the CCCD is not available we return false.
		final BluetoothGatt gatt = mGatt;
		final BluetoothGattService genericAttributeService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE_UUID);
		if (genericAttributeService == null)
			return false;

		final BluetoothGattCharacteristic serviceChangedCharacteristic = genericAttributeService.getCharacteristic(SERVICE_CHANGED_UUID);
		if (serviceChangedCharacteristic == null)
			return false;

		final BluetoothGattDescriptor descriptor = serviceChangedCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
		if (descriptor == null)
			return false;

		mRequestCompleted = false;
		mError = 0;

		logi("Reading Service Changed CCCD value...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Reading Service Changed CCCD value...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.readDescriptor(" + descriptor.getUuid() + ")");
		gatt.readDescriptor(descriptor);

		// We have to wait until device receives a response or an error occur
		try {
			synchronized (mLock) {
				while ((!mRequestCompleted && mConnected && mError == 0) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mError != 0)
			throw new DfuException("Unable to read Service Changed CCCD", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read Service Changed CCCD: device disconnected");

		// Return true if the CCCD value is
		return descriptor.getValue() != null && descriptor.getValue().length == 2
				&& descriptor.getValue()[0] == BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0]
				&& descriptor.getValue()[1] == BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[1];
	}

	/**
	 * Writes the operation code to the characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)} will be called or
	 * the device gets disconnected. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param characteristic the characteristic to write to. Should be the DFU CONTROL POINT
	 * @param value          the value to write to the characteristic
	 * @param reset          whether the command trigger restarting the device
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected void writeOpCode(final BluetoothGattCharacteristic characteristic, final byte[] value, final boolean reset) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		if (mAborted)
			throw new UploadAbortedException();
		mReceivedData = null;
		mError = 0;
		mRequestCompleted = false;
		/*
		 * Sending a command that will make the DFU target to reboot may cause an error 133 (0x85 - Gatt Error). If so, with this flag set, the error will not be shown to the user
		 * as the peripheral is disconnected anyway. See: mGattCallback#onCharacteristicWrite(...) method
		 */
		mResetRequestSent = reset;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
		characteristic.setValue(value);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		mGatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((!mRequestCompleted && mConnected && mError == 0) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (!mResetRequestSent && mError != 0)
			throw new DfuException("Unable to write Op Code " + value[0], mError);
		if (!mResetRequestSent && !mConnected)
			throw new DeviceDisconnectedException("Unable to write Op Code " + value[0] + ": device disconnected");
	}

	/**
	 * Creates bond to the device. Works on all APIs since 18th (Android 4.3).
	 *
	 * @return true if it's already bonded or the bonding has started
	 */
	@SuppressLint("NewApi")
	protected boolean createBond() {
		final BluetoothDevice device = mGatt.getDevice();
		if (device.getBondState() == BluetoothDevice.BOND_BONDED)
			return true;

		boolean result;
		mRequestCompleted = false;

		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Starting pairing...");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.getDevice().createBond()");
			result = device.createBond();
		} else {
			result = createBondApi18(device);
		}

		// We have to wait until device is bounded
		try {
			synchronized (mLock) {
				while (!mRequestCompleted && !mAborted)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		return result;
	}

	/**
	 * A method that creates the bond to given device on API lower than Android 5.
	 *
	 * @param device the target device
	 * @return false if bonding failed (no hidden createBond() method in BluetoothDevice, or this method returned false
	 */
	private boolean createBondApi18(final BluetoothDevice device) {
		/*
		 * There is a createBond() method in BluetoothDevice class but for now it's hidden. We will call it using reflections. It has been revealed in KitKat (Api19)
		 */
		try {
			final Method createBond = device.getClass().getMethod("createBond");
			if (createBond != null) {
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.getDevice().createBond() (hidden)");
				return (Boolean) createBond.invoke(device);
			}
		} catch (final Exception e) {
			Log.w(TAG, "An exception occurred while creating bond", e);
		}
		return false;
	}

	/**
	 * Removes the bond information for the given device.
	 *
	 *  @return <code>true</code> if operation succeeded, <code>false</code> otherwise
	 */
	protected boolean removeBond() {
		final BluetoothDevice device = mGatt.getDevice();
		if (device.getBondState() == BluetoothDevice.BOND_NONE)
			return true;

		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Removing bond information...");
		boolean result = false;
		/*
		 * There is a removeBond() method in BluetoothDevice class but for now it's hidden. We will call it using reflections.
		 */
		try {
			final Method removeBond = device.getClass().getMethod("removeBond");
			if (removeBond != null) {
				mRequestCompleted = false;
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.getDevice().removeBond() (hidden)");
				result = (Boolean) removeBond.invoke(device);

				// We have to wait until device is unbounded
				try {
					synchronized (mLock) {
						while (!mRequestCompleted && !mAborted)
							mLock.wait();
					}
				} catch (final InterruptedException e) {
					loge("Sleeping interrupted", e);
				}
			}
			result = true;
		} catch (final Exception e) {
			Log.w(TAG, "An exception occurred while removing bond information", e);
		}
		return result;
	}

	/**
	 * Waits until the notification will arrive. Returns the data returned by the notification. This method will block the thread until response is not ready or
	 * the device gets disconnected. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @return the value returned by the Control Point notification
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected byte[] readNotificationResponse() throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		// do not clear the mReceiveData here. The response might already be obtained. Clear it in write request instead.
		try {
			synchronized (mLock) {
				while ((mReceivedData == null && mConnected && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Op Code", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to write Op Code: device disconnected");
		return mReceivedData;
	}

	protected String parse(final byte[] data) {
		if (data == null)
			return "";

		final int length = data.length;
		if (length == 0)
			return "";

		final char[] out = new char[length * 3 - 1];
		for (int j = 0; j < length; j++) {
			int v = data[j] & 0xFF;
			out[j * 3] = HEX_ARRAY[v >>> 4];
			out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
			if (j != length - 1)
				out[j * 3 + 2] = '-';
		}
		return new String(out);
	}

	protected void loge(final String message) {
		Log.e(TAG, message);
	}

	protected void loge(final String message, final Throwable e) {
		Log.e(TAG, message, e);
	}

	protected void logw(final String message) {
		if (DfuBaseService.DEBUG)
			Log.w(TAG, message);
	}

	protected void logi(final String message) {
		if (DfuBaseService.DEBUG)
			Log.i(TAG, message);
	}
}
