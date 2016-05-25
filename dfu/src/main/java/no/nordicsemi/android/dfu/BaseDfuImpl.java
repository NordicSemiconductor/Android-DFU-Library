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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ abstract class BaseDfuImpl {
	public static final String TAG = "DfuImpl";

	protected static final UUID GENERIC_ATTRIBUTE_SERVICE_UUID = new UUID(0x0000180100001000L, 0x800000805F9B34FBL);
	protected static final UUID SERVICE_CHANGED_UUID = new UUID(0x00002A0500001000L, 0x800000805F9B34FBL);
	protected static final UUID CLIENT_CHARACTERISTIC_CONFIG = new UUID(0x0000290200001000L, 0x800000805f9b34fbL);

	private static final int NOTIFICATIONS = 1;
	private static final int INDICATIONS = 2;
	protected static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	protected static final int MAX_PACKET_SIZE = 20; // the maximum number of bytes in one packet is 20. May be less.

	/**
	 * The current connection state. If its value is > 0 than an error has occurred. Error number is a negative value of mConnectionState
	 */
	protected int mConnectionState;
	protected final static int STATE_DISCONNECTED = 0;
	protected final static int STATE_CONNECTING = -1;
	protected final static int STATE_CONNECTED = -2;
	protected final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
	protected final static int STATE_DISCONNECTING = -4;
	protected final static int STATE_CLOSED = -5;

	/**
	 * Lock used in synchronization purposes
	 */
	protected final Object mLock = new Object();

	protected final InputStream mFirmwareStream;
	protected final InputStream mInitPacketStream;

	/** Flag set to true if sending was paused. */
	protected boolean mPaused;
	/** Flag set to true if sending was aborted. */
	protected boolean mAborted;
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
	/**
	 * The target device address
	 */
	private String mDeviceAddress;
	private BluetoothAdapter mBluetoothAdapter;
	protected DfuBaseService mService;
	protected final DfuProgressInfo mProgressInfo;

	private final BroadcastReceiver mConnectionStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// Obtain the device and check it this is the one that we are connected to
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (!device.getAddress().equals(mDeviceAddress))
				return;

			final String action = intent.getAction();

			logi("Action received: " + action);
			mConnectionState = STATE_DISCONNECTED;
			notifyLock();
		}
	};

	private final BroadcastReceiver mBondStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// Obtain the device and check it this is the one that we are connected to
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (!device.getAddress().equals(mDeviceAddress))
				return;

			// Read bond state
			final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
			if (bondState == BluetoothDevice.BOND_BONDING)
				return;

			mRequestCompleted = true;
			notifyLock();
		}
	};

	protected class BaseBluetoothGattCallback extends BluetoothGattCallback {
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			// Check whether an error occurred
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					logi("Connected to GATT server");
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Connected to " + mDeviceAddress);
					mConnectionState = STATE_CONNECTED;

					/*
					 *  The onConnectionStateChange callback is called just after establishing connection and before sending Encryption Request BLE event in case of a paired device.
					 *  In that case and when the Service Changed CCCD is enabled we will get the indication after initializing the encryption, about 1600 milliseconds later.
					 *  If we discover services right after connecting, the onServicesDiscovered callback will be called immediately, before receiving the indication and the following
					 *  service discovery and we may end up with old, application's services instead.
					 *
					 *  This is to support the buttonless switch from application to bootloader mode where the DFU bootloader notifies the master about service change.
					 *  Tested on Nexus 4 (Android 4.4.4 and 5), Nexus 5 (Android 5), Samsung Note 2 (Android 4.4.2). The time after connection to end of service discovery is about 1.6s
					 *  on Samsung Note 2.
					 *
					 *  NOTE: We are doing this to avoid the hack with calling the hidden gatt.refresh() method, at least for bonded devices.
					 */
					if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
						try {
							synchronized (this) {
								logd("Waiting 1600 ms for a possible Service Changed indication...");
								mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "wait(1600)");
								wait(1600);

								// After 1.6s the services are already discovered so the following gatt.discoverServices() finishes almost immediately.

								// NOTE: This also works with shorted waiting time. The gatt.discoverServices() must be called after the indication is received which is
								// about 600ms after establishing connection. Values 600 - 1600ms should be OK.
							}
						} catch (final InterruptedException e) {
							// Do nothing
						}
					}

					// Attempts to discover services after successful connection.
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Discovering services...");
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.discoverServices()");
					final boolean success = gatt.discoverServices();
					logi("Attempting to start service discovery... " + (success ? "succeed" : "failed"));

					if (!success) {
						mError = DfuBaseService.ERROR_SERVICE_DISCOVERY_NOT_STARTED;
					} else {
						// Just return here, lock will be notified when service discovery finishes
						return;
					}
				} else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					logi("Disconnected from GATT server");
					mPaused = false;
					mConnectionState = STATE_DISCONNECTED;
				}
			} else {
				loge("Connection state change error: " + status + " newState: " + newState);
				if (newState == BluetoothGatt.STATE_DISCONNECTED)
					mConnectionState = STATE_DISCONNECTED;
				mPaused = false;
				mError = DfuBaseService.ERROR_CONNECTION_STATE_MASK | status;
			}
			notifyLock();
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				logi("Services discovered");
				mConnectionState = STATE_CONNECTED_AND_READY;
			} else {
				loge("Service discovery error: " + status);
				mError = DfuBaseService.ERROR_CONNECTION_MASK | status;
			}
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

		// This method is repeated here and in the service class for performance matters.
		protected String parse(final BluetoothGattCharacteristic characteristic) {
			final byte[] data = characteristic.getValue();
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

	/* package */ BaseDfuImpl(final DfuBaseService service, final InputStream firmwareStream, final InputStream initPacketStream) {
		mService = service;
		mFirmwareStream = firmwareStream;
		mInitPacketStream = initPacketStream;
		int size;
		try {
			size = firmwareStream.available();
		} catch (final IOException e) {
			size = 0;
			// not possible
		}
		mProgressInfo = new DfuProgressInfo(size);

		final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		service.registerReceiver(mConnectionStateBroadcastReceiver, filter);

		final IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		service.registerReceiver(mBondStateBroadcastReceiver, bondFilter);
	}

	/* package */ void unregister() {
		mService.unregisterReceiver(mConnectionStateBroadcastReceiver);
		mService.unregisterReceiver(mBondStateBroadcastReceiver);
		mService = null;
	}

	/* package */ void pause() {
		mPaused = true;
	}

	/* package */ void resume() {
		mPaused = false;
		notifyLock();
	}

	/* package */ void abort() {
		mPaused = false;
		mAborted = true;
		notifyLock();
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
	protected abstract BluetoothGattCallback getGattCallback();

	/**
	 * Connects to the BLE device with given address. This method is SYNCHRONOUS, it wait until the connection status change from {@link #STATE_CONNECTING} to {@link #STATE_CONNECTED_AND_READY} or an
	 * error occurs. This method returns <code>null</code> if Bluetooth adapter is disabled.
	 *
	 * @param address the device address
	 * @return the GATT device or <code>null</code> if Bluetooth adapter is disabled.
	 */
	protected BluetoothGatt connect(final String address) {
		if (!mBluetoothAdapter.isEnabled())
			return null;

		mConnectionState = STATE_CONNECTING;

		logi("Connecting to the device...");
		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt = device.connectGatt(autoConnect = false)");
		final BluetoothGatt gatt = device.connectGatt(mService, false, getGattCallback());

		// We have to wait until the device is connected and services are discovered
		// Connection error may occur as well.
		try {
			synchronized (mLock) {
				while (((mConnectionState == STATE_CONNECTING || mConnectionState == STATE_CONNECTED) && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		return gatt;
	}

	/**
	 * Disconnects from the device and cleans local variables in case of error. This method is SYNCHRONOUS and wait until the disconnecting process will be completed.
	 *
	 * @param gatt  the GATT device to be disconnected
	 * @param error error number
	 */
	protected void terminateConnection(final BluetoothGatt gatt, final int error) {
		if (mConnectionState != STATE_DISCONNECTED) {
			// Disconnect from the device
			disconnect(gatt);
		}

		// Close the device
		refreshDeviceCache(gatt, false); // This should be set to true when DFU Version is 0.5 or lower
		close(gatt);
		mService.updateProgressNotification(error);
	}

	/**
	 * Disconnects from the device. This is SYNCHRONOUS method and waits until the callback returns new state. Terminates immediately if device is already disconnected. Do not call this method
	 * directly, use {@link #terminateConnection(android.bluetooth.BluetoothGatt, int)} instead.
	 *
	 * @param gatt the GATT device that has to be disconnected
	 */
	protected void disconnect(final BluetoothGatt gatt) {
		if (mConnectionState == STATE_DISCONNECTED)
			return;

		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Disconnecting...");
		mService.updateProgressNotification(mProgressInfo.setProgress(DfuBaseService.PROGRESS_DISCONNECTING));

		mConnectionState = STATE_DISCONNECTING;

		logi("Disconnecting from the device...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.disconnect()");
		gatt.disconnect();

		// We have to wait until device gets disconnected or an error occur
		waitUntilDisconnected();
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected");
	}

	/**
	 * Wait until the connection state will change to {@link #STATE_DISCONNECTED} or until an error occurs.
	 */
	protected void waitUntilDisconnected() {
		try {
			synchronized (mLock) {
				while (mConnectionState != STATE_DISCONNECTED && mError == 0)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
	}

	/**
	 * Closes the GATT device and cleans up.
	 *
	 * @param gatt the GATT device to be closed
	 */
	protected void close(final BluetoothGatt gatt) {
		logi("Cleaning up...");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.close()");
		gatt.close();
		mConnectionState = STATE_CLOSED;
	}

	/**
	 * Clears the device cache. After uploading new firmware the DFU target will have other services than before.
	 *
	 * @param gatt  the GATT device to be refreshed
	 * @param force <code>true</code> to force the refresh
	 */
	protected void refreshDeviceCache(final BluetoothGatt gatt, final boolean force) {
		/*
		 * If the device is bonded this is up to the Service Changed characteristic to notify Android that the services has changed.
		 * There is no need for this trick in that case.
		 * If not bonded, the Android should not keep the services cached when the Service Changed characteristic is present in the target device database.
		 * However, due to the Android bug (still exists in Android 5.0.1), it is keeping them anyway and the only way to clear services is by using this hidden refresh method.
		 */
		if (force || gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.refresh() (hidden)");
			/*
			 * There is a refresh() method in BluetoothGatt class but for now it's hidden. We will call it using reflections.
			 */
			try {
				final Method refresh = gatt.getClass().getMethod("refresh");
				if (refresh != null) {
					final boolean success = (Boolean) refresh.invoke(gatt);
					logi("Refreshing result: " + success);
				}
			} catch (Exception e) {
				loge("An exception occurred while refreshing device", e);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Refreshing failed");
			}
		}
	}

	/**
	 * Enables or disables the notifications for given characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onDescriptorWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattDescriptor, int)} will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}. If
	 * connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to enable or disable notifications for
	 * @param type           {@link #NOTIFICATIONS} or {@link #INDICATIONS}
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected void enableCCCD(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int type) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		final String debugString = type == NOTIFICATIONS ? "notifications" : "indications";
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to set " + debugString + " state", mConnectionState);

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
				while ((!cccdEnabled && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused) {
					mLock.wait();
					// Check the value of the CCCD
					cccdEnabled = descriptor.getValue() != null && descriptor.getValue().length == 2 && descriptor.getValue()[0] > 0 && descriptor.getValue()[1] == 0;
				}
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to set " + debugString + " state", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to set " + debugString + " state", mConnectionState);
	}

	/**
	 * Reads the value of the Service Changed Client Characteristic Configuration descriptor (CCCD).
	 *
	 * @param gatt           the GATT device
	 * @return <code>true</code> if Service Changed CCCD is enabled and set to INDICATE
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private boolean isServiceChangedCCCDEnabled(final BluetoothGatt gatt) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read Service Changed CCCD", mConnectionState);
		// If the Service Changed characteristic or the CCCD is not available we return false.
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

		gatt.readDescriptor(descriptor);

		// We have to wait until device receives a response or an error occur
		try {
			synchronized (mLock) {
				while ((!mRequestCompleted && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to read Service Changed CCCD", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read Service Changed CCCD", mConnectionState);

		// Return true if the CCCD value is
		return descriptor.getValue() != null && descriptor.getValue().length == 2
				&& descriptor.getValue()[0] == BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[0]
				&& descriptor.getValue()[1] == BluetoothGattDescriptor.ENABLE_INDICATION_VALUE[1];
	}

	/**
	 * Writes the operation code to the characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)} will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to write to. Should be the DFU CONTROL POINT
	 * @param value          the value to write to the characteristic
	 * @param reset          whether the command trigger restarting the device
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected void writeOpCode(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value, final boolean reset) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
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
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((!mRequestCompleted && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (!mResetRequestSent && mError != 0)
			throw new DfuException("Unable to write Op Code " + value[0], mError);
		if (!mResetRequestSent && mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Op Code " + value[0], mConnectionState);
	}

	@SuppressLint("NewApi")
	protected boolean createBond(final BluetoothDevice device) {
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

	protected boolean createBondApi18(final BluetoothDevice device) {
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
	 * @param device the device to unbound
	 * @return <code>true</code> if operation succeeded, <code>false</code> otherwise
	 */
	protected boolean removeBond(final BluetoothDevice device) {
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
	 * Waits until the notification will arrive. Returns the data returned by the notification. This method will block the thread if response is not ready or connection state will change from
	 * {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @return the value returned by the Control Point notification
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	protected byte[] readNotificationResponse() throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		// do not clear the mReceiveData here. The response might already be obtained. Clear it in write request instead.
		mError = 0;
		try {
			synchronized (mLock) {
				while ((mReceivedData == null && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Op Code", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Op Code", mConnectionState);
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
//		if (BuildConfig.DEBUG)
			Log.w(TAG, message);
	}

	protected void logi(final String message) {
//		if (BuildConfig.DEBUG)
			Log.i(TAG, message);
	}

	protected void logd(final String message) {
//		if (BuildConfig.DEBUG)
			Log.d(TAG, message);
	}
}
