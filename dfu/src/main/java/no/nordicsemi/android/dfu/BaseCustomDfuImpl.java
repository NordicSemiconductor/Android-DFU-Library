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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

import java.io.IOException;
import java.util.UUID;
import java.util.zip.CRC32;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ abstract class BaseCustomDfuImpl extends BaseDfuImpl {
	/**
	 * Flag indicating whether the init packet has been already transferred or not.
	 */
	private boolean mInitPacketInProgress;
	/**
	 * Flag indicating whether the firmware is being transmitted or not.
	 */
	private boolean mFirmwareUploadInProgress;
	/**
	 * The number of packets of firmware data to be send before receiving a new Packets receipt notification. 0 disables the packets notifications.
	 */
	protected final int mPacketsBeforeNotification;
	/**
	 * The number of packets sent since last notification.
	 */
	protected int mPacketsSentSinceNotification;
	/**
	 * <p>
	 * Flag set to <code>true</code> when the DFU target had send a notification with status other than success. Setting it to <code>true</code> will abort sending firmware and
	 * stop logging notifications (read below for explanation).
	 * </p>
	 * <p>
	 * The onCharacteristicWrite(..) callback is called when Android writes the packet into the outgoing queue, not when it physically sends the data.
	 * This means that the service will first put up to N* packets, one by one, to the queue, while in fact the first one is transmitted.
	 * In case the DFU target is in an invalid state it will notify Android with a notification 10-03-02 for each packet of firmware that has been sent.
	 * After receiving the first such notification, the DFU service will add the reset command to the outgoing queue, but it will still be receiving such notifications
	 * until all the data packets are sent. Those notifications should be ignored. This flag will prevent from logging "Notification received..." more than once.
	 * </p>
	 * <p>
	 * Additionally, sometimes after writing the command 6 ({@link LegacyDfuImpl#OP_CODE_RESET}), Android will receive a notification and update the characteristic value with 10-03-02 and the callback for write
	 * reset command will log "[DFU] Data written to ..., value (0x): 10-03-02" instead of "...(x0): 06". But this does not matter for the DFU process.
	 * </p>
	 * <p>
	 * N* - Value of Packet Receipt Notification, 12 by default.
	 * </p>
	 */
	protected boolean mRemoteErrorOccurred;

	protected class BaseCustomBluetoothCallback extends BaseBluetoothGattCallback {
		protected void onPacketCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			// this method can be overwritten on the final class
		}

		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				/*
				 * This method is called when either a CONTROL POINT or PACKET characteristic has been written.
				 * If it is the CONTROL POINT characteristic, just set the {@link mRequestCompleted} flag to true. The main thread will continue its task when notified.
				 * If the PACKET characteristic was written we must:
				 * - if the image size was written in DFU Start procedure, just set flag to true
				 * otherwise
				 * - send the next packet, if notification is not required at that moment, or
				 * - do nothing, because we have to wait for the notification to confirm the data received
				 */
				if (characteristic.getUuid().equals(getPacketCharacteristicUUID())) {
					if (mInitPacketInProgress) {
						// We've got confirmation that the init packet was sent
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
						mInitPacketInProgress = false;
					} else if (mFirmwareUploadInProgress) {
						// If the PACKET characteristic was written with image data, update counters
						mProgressInfo.addBytesSent(characteristic.getValue().length);
						mPacketsSentSinceNotification++;

						final boolean notificationExpected = mPacketsBeforeNotification > 0 && mPacketsSentSinceNotification == mPacketsBeforeNotification;
						final boolean lastPacketTransferred = mProgressInfo.isComplete();
						final boolean lastObjectPacketTransferred = mProgressInfo.isObjectComplete();

						// When a Packet Receipt Notification notification is expected
						// we must not call notifyLock() as the process will resume after notification is received.
						if (notificationExpected)
							return;

						// In Secure DFU we (usually, depends on the page size and PRN value) do not get any notification after the object is completed,
						// therefor the lock must be notified here to resume the main process.
						if (lastPacketTransferred || lastObjectPacketTransferred) {
							mFirmwareUploadInProgress = false;
							notifyLock();
							return;
						}

						// When neither of them is true, send the next packet
						try {
							waitIfPaused();
							// The writing might have been aborted (mAborted = true), an error might have occurred.
							// In that case stop sending.
							if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
								mFirmwareUploadInProgress = false;
								mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Upload terminated");
								notifyLock();
								return;
							}

							final int available = mProgressInfo.getAvailableObjectSizeIsBytes();
							byte[] buffer = mBuffer;
							if (available < 20)
								buffer = new byte[available];
							final int size = mFirmwareStream.read(buffer);
							writePacket(gatt, characteristic, buffer, size);
							return;
						} catch (final HexFileValidationException e) {
							loge("Invalid HEX file");
							mError = DfuBaseService.ERROR_FILE_INVALID;
						} catch (final IOException e) {
							loge("Error while reading the input stream", e);
							mError = DfuBaseService.ERROR_FILE_IO_EXCEPTION;
						}
					} else {
						onPacketCharacteristicWrite(gatt, characteristic, status);
					}
				} else {
					// If the CONTROL POINT characteristic was written just set the flag to true. The main thread will continue its task when notified.
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
					mRequestCompleted = true;
				}
			} else {
				/*
				 * If a Reset (Op Code = 6) or Activate and Reset (Op Code = 5) commands are sent, the DFU target resets and sometimes does it so quickly that does not manage to send
				 * any ACK to the controller and error 133 is thrown here. This bug should be fixed in SDK 8.0+ where the target would gracefully disconnect before restarting.
				 */
				if (mResetRequestSent)
					mRequestCompleted = true;
				else {
					loge("Characteristic write error: " + status);
					mError = DfuBaseService.ERROR_CONNECTION_MASK | status;
				}
			}
			notifyLock();
		}

		protected void handlePacketReceiptNotification(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			// Secure DFU:
			// When PRN is set to be received after the object is complete we don't want to send anything. First the object needs to be executed.
			if (!mFirmwareUploadInProgress) {
				handleNotification(gatt, characteristic);
				return;
			}

			final BluetoothGattCharacteristic packetCharacteristic = gatt.getService(getDfuServiceUUID()).getCharacteristic(getPacketCharacteristicUUID());
			try {
				mPacketsSentSinceNotification = 0;

				waitIfPaused();
				// The writing might have been aborted (mAborted = true), an error might have occurred.
				// In that case quit sending.
				if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
					mFirmwareUploadInProgress = false;
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Upload terminated");
					return;
				}

				final boolean lastPacketTransferred = mProgressInfo.isComplete();
				final boolean lastObjectPacketTransferred = mProgressInfo.isObjectComplete();

				if (lastPacketTransferred || lastObjectPacketTransferred) {
					mFirmwareUploadInProgress = false;
					notifyLock();
					return;
				}

				final int available = mProgressInfo.getAvailableObjectSizeIsBytes();
				byte[] buffer = mBuffer;
				if (available < 20)
					buffer = new byte[available];
				final int size = mFirmwareStream.read(buffer);
				writePacket(gatt, packetCharacteristic, buffer, size);
			} catch (final HexFileValidationException e) {
				loge("Invalid HEX file");
				mError = DfuBaseService.ERROR_FILE_INVALID;
			} catch (final IOException e) {
				loge("Error while reading the input stream", e);
				mError = DfuBaseService.ERROR_FILE_IO_EXCEPTION;
			}
		}

		protected void handleNotification(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Notification received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
			mReceivedData = characteristic.getValue();
			mFirmwareUploadInProgress = false;
		}
	}

	BaseCustomDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);

		if (intent.hasExtra(DfuBaseService.EXTRA_PACKET_RECEIPT_NOTIFICATIONS_ENABLED)) {
			// Read from intent
			final boolean packetReceiptNotificationEnabled = intent.getBooleanExtra(DfuBaseService.EXTRA_PACKET_RECEIPT_NOTIFICATIONS_ENABLED, Build.VERSION.SDK_INT < Build.VERSION_CODES.M);
			int numberOfPackets = intent.getIntExtra(DfuBaseService.EXTRA_PACKET_RECEIPT_NOTIFICATIONS_VALUE, DfuServiceInitiator.DEFAULT_PRN_VALUE);
			if (numberOfPackets < 0 || numberOfPackets > 0xFFFF)
				numberOfPackets = DfuServiceInitiator.DEFAULT_PRN_VALUE;
			if (!packetReceiptNotificationEnabled)
				numberOfPackets = 0;
			mPacketsBeforeNotification = numberOfPackets;
		} else {
			// Read preferences
			final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(service);
			final boolean packetReceiptNotificationEnabled = preferences.getBoolean(DfuSettingsConstants.SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED, Build.VERSION.SDK_INT < Build.VERSION_CODES.M);
			String value = preferences.getString(DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS, String.valueOf(DfuServiceInitiator.DEFAULT_PRN_VALUE));
			int numberOfPackets;
			try {
				numberOfPackets = Integer.parseInt(value);
				if (numberOfPackets < 0 || numberOfPackets > 0xFFFF)
					numberOfPackets = DfuServiceInitiator.DEFAULT_PRN_VALUE;
			} catch (final NumberFormatException e) {
				numberOfPackets = DfuServiceInitiator.DEFAULT_PRN_VALUE;
			}
			if (!packetReceiptNotificationEnabled)
				numberOfPackets = 0;
			mPacketsBeforeNotification = numberOfPackets;
		}
	}

	protected abstract UUID getControlPointCharacteristicUUID();

	protected abstract UUID getPacketCharacteristicUUID();

	protected abstract UUID getDfuServiceUUID();

	/**
	 * Wends the whole init packet stream to the given characteristic.
	 * @param characteristic the target characteristic
	 * @throws DfuException
	 * @throws DeviceDisconnectedException
	 * @throws UploadAbortedException
	 */
	protected void writeInitData(final BluetoothGattCharacteristic characteristic, final CRC32 crc32) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		try {
			byte[] data = new byte[20];
			int size;
			while ((size = mInitPacketStream.read(data, 0, data.length)) != -1) {
				writeInitPacket(characteristic, data, size);
				if (crc32 != null)
					crc32.update(data, 0, size);
			}
		} catch (final IOException e) {
			loge("Error while reading Init packet file", e);
			throw new DfuException("Error while reading Init packet file", DfuBaseService.ERROR_FILE_ERROR);
		}
	}

	/**
	 * Writes the Init packet to the characteristic. This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the device gets disconnected. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param buffer         the init packet as a byte array. This must be shorter or equal to 20 bytes (TODO check this restriction).
	 * @param size           the init packet size
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeInitPacket(final BluetoothGattCharacteristic characteristic, final byte[] buffer, final int size) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		if (mAborted)
			throw new UploadAbortedException();
		byte[] locBuffer = buffer;
		if (buffer.length != size) {
			locBuffer = new byte[size];
			System.arraycopy(buffer, 0, locBuffer, 0, size);
		}
		mReceivedData = null;
		mError = 0;
		mInitPacketInProgress = true;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(locBuffer);
		logi("Sending init packet (Value = " + parse(locBuffer) + ")");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		mGatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mInitPacketInProgress && mConnected && mError == 0) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mError != 0)
			throw new DfuException("Unable to write Init DFU Parameters", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to write Init DFU Parameters: device disconnected");
	}

	/**
	 * Starts sending the data. This method is SYNCHRONOUS and terminates when the whole file will be uploaded or the device get disconnected.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param packetCharacteristic the characteristic to write file content to. Must be the DFU PACKET
	 * @throws DeviceDisconnectedException Thrown when the device will disconnect in the middle of the transmission.
	 * @throws DfuException                Thrown if DFU error occur
	 * @throws UploadAbortedException
	 */
	protected void uploadFirmwareImage(final BluetoothGattCharacteristic packetCharacteristic) throws DeviceDisconnectedException,
			DfuException, UploadAbortedException {
		if (mAborted)
			throw new UploadAbortedException();
		mReceivedData = null;
		mError = 0;
		mFirmwareUploadInProgress = true;
		mPacketsSentSinceNotification = 0;

		final byte[] buffer = mBuffer;
		try {
			final int size = mFirmwareStream.read(buffer);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Sending firmware to characteristic " + packetCharacteristic.getUuid() + "...");
			writePacket(mGatt, packetCharacteristic, buffer, size);
		} catch (final HexFileValidationException e) {
			throw new DfuException("HEX file not valid", DfuBaseService.ERROR_FILE_INVALID);
		} catch (final IOException e) {
			throw new DfuException("Error while reading file", DfuBaseService.ERROR_FILE_IO_EXCEPTION);
		}

		try {
			synchronized (mLock) {
				while ((mFirmwareUploadInProgress && mReceivedData == null && mConnected && mError == 0) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}

		if (mError != 0)
			throw new DfuException("Uploading Firmware Image failed", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Uploading Firmware Image failed: device disconnected");
	}

	/**
	 * Writes the buffer to the characteristic. The maximum size of the buffer is 20 bytes. This method is ASYNCHRONOUS and returns immediately after adding the data to TX queue.
	 *
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param buffer         the buffer with 1-20 bytes
	 * @param size           the number of bytes from the buffer to send
	 */
	private void writePacket(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] buffer, final int size) {
		byte[] locBuffer = buffer;
		if (size <= 0) // This should never happen
			return;
		if (buffer.length != size) {
			locBuffer = new byte[size];
			System.arraycopy(buffer, 0, locBuffer, 0, size);
		}
		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(locBuffer);
		gatt.writeCharacteristic(characteristic);
		// FIXME BLE buffer overflow
		// after writing to the device with WRITE_NO_RESPONSE property the onCharacteristicWrite callback is received immediately after writing data to a buffer.
		// The real sending is much slower than adding to the buffer. This method does not return false if writing didn't succeed.. just the callback is not invoked.
		//
		// More info: this works fine on Nexus 5 (Android 4.4) (4.3 seconds) and on Samsung S4 (Android 4.3) (20 seconds) so this is a driver issue.
		// Nexus 4 and 7 uses Qualcomm chip, Nexus 5 and Samsung uses Broadcom chips.
	}

	/**
	 * Closes the BLE connection to the device and removes/restores bonding, if a proper flags were set in the {@link DfuServiceInitiator}.
	 * This method will also change the DFU state to completed or restart the service to send the second part.
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
		boolean alreadyWaited = false;
		if (mGatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
			final boolean restoreBond = intent.getBooleanExtra(DfuBaseService.EXTRA_RESTORE_BOND, false);
			if (restoreBond || !keepBond) {
				// The bond information was lost.
				removeBond();

				// Give some time for removing the bond information. 300ms was to short, let's set it to 2 seconds just to be sure.
				mService.waitFor(2000);
				alreadyWaited = true;
			}

			if (restoreBond && (mFileType & DfuBaseService.TYPE_APPLICATION) > 0) {
				// Restore pairing when application was updated.
				createBond();
				alreadyWaited = false;
			}
		}

		/*
		 * We need to send PROGRESS_COMPLETED message only when all files has been transmitted.
		 * In case you want to send the Soft Device and/or Bootloader and the Application, the service will be started twice: one to send SD+BL, and the
		 * second time to send the Application only (using the new Bootloader). In the first case we do not send PROGRESS_COMPLETED notification.
		 */
		if (mProgressInfo.isLastPart()) {
			// Delay this event a little bit. Android needs some time to prepare for reconnection.
			if (!alreadyWaited)
				mService.waitFor(1400);
			mProgressInfo.setProgress(DfuBaseService.PROGRESS_COMPLETED);
		} else {
			/*
			 * In case when the SoftDevice has been upgraded, and the application should be send in the following connection, we have to
			 * make sure that we know the address the device is advertising with. Depending on the method used to start the DFU bootloader the first time
			 * the new Bootloader may advertise with the same address or one incremented by 1.
			 * When the buttonless update was used, the bootloader will use the same address as the application. The cached list of services on the Android device
			 * should be cleared thanks to the Service Changed characteristic (the fact that it exists if not bonded, or the Service Changed indication on bonded one).
			 * In case of forced DFU mode (using a button), the Bootloader does not know whether there was the Service Changed characteristic present in the list of
			 * application's services so it must advertise with a different address. The same situation applies when the new Soft Device was uploaded and the old
			 * application has been removed in this process.
			 *
			 * We could have save the fact of jumping as a parameter of the service but it ma be that some Android devices must first scan a device before connecting to it.
			 * It a device with the address+1 has never been detected before the service could have failed on connection.
			 */

			/*
			 * The current service instance has uploaded the Soft Device and/or Bootloader.
			 * We need to start another instance that will try to send application only.
			 */
			logi("Starting service that will upload application");
			final Intent newIntent = new Intent();
			newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
			newIntent.putExtra(DfuBaseService.EXTRA_FILE_MIME_TYPE, DfuBaseService.MIME_TYPE_ZIP); // ensure this is set (e.g. for scripts)
			newIntent.putExtra(DfuBaseService.EXTRA_FILE_TYPE, DfuBaseService.TYPE_APPLICATION); // set the type to application only
			newIntent.putExtra(DfuBaseService.EXTRA_PART_CURRENT, mProgressInfo.getCurrentPart() + 1);
			newIntent.putExtra(DfuBaseService.EXTRA_PARTS_TOTAL, mProgressInfo.getTotalParts());
			restartService(newIntent, /* the bootloader may advertise with different address */ true);
		}
	}
}
