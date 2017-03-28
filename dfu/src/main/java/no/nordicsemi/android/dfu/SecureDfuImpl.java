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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.CRC32;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuExtendedErrorException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.error.SecureDfuError;

/* package */ class SecureDfuImpl extends BaseCustomDfuImpl {
	// UUIDs used by the DFU
	protected static final UUID DEFAULT_DFU_SERVICE_UUID       = new UUID(0x0000FE5900001000L, 0x800000805F9B34FBL); // 16-bit UUID assigned by Bluetooth SIG
	protected static final UUID DEFAULT_DFU_CONTROL_POINT_UUID = new UUID(0x8EC90001F3154F60L, 0x9FB8838830DAEA50L);
	protected static final UUID DEFAULT_DFU_PACKET_UUID        = new UUID(0x8EC90002F3154F60L, 0x9FB8838830DAEA50L);

	protected static UUID DFU_SERVICE_UUID       = DEFAULT_DFU_SERVICE_UUID;
	protected static UUID DFU_CONTROL_POINT_UUID = DEFAULT_DFU_CONTROL_POINT_UUID;
	protected static UUID DFU_PACKET_UUID        = DEFAULT_DFU_PACKET_UUID;

	private static final int DFU_STATUS_SUCCESS = 1;
	private static final int MAX_ATTEMPTS = 3;

	// Object types
	private static final int OBJECT_COMMAND = 0x01;
	private static final int OBJECT_DATA = 0x02;
	// Operation codes and packets
	private static final int OP_CODE_CREATE_KEY = 0x01;
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY = 0x02;
	private static final int OP_CODE_CALCULATE_CHECKSUM_KEY = 0x03;
	private static final int OP_CODE_EXECUTE_KEY = 0x04;
	private static final int OP_CODE_SELECT_OBJECT_KEY = 0x06;
	private static final int OP_CODE_RESPONSE_CODE_KEY = 0x60;
	private static final byte[] OP_CODE_CREATE_COMMAND = new byte[]{OP_CODE_CREATE_KEY, OBJECT_COMMAND, 0x00, 0x00, 0x00, 0x00 };
	private static final byte[] OP_CODE_CREATE_DATA = new byte[]{OP_CODE_CREATE_KEY, OBJECT_DATA, 0x00, 0x00, 0x00, 0x00};
	private static final byte[] OP_CODE_PACKET_RECEIPT_NOTIF_REQ = new byte[]{OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY, 0x00, 0x00 /* param PRN uint16 in Little Endian */};
	private static final byte[] OP_CODE_CALCULATE_CHECKSUM = new byte[]{OP_CODE_CALCULATE_CHECKSUM_KEY};
	private static final byte[] OP_CODE_EXECUTE = new byte[]{OP_CODE_EXECUTE_KEY};
	private static final byte[] OP_CODE_SELECT_OBJECT = new byte[]{OP_CODE_SELECT_OBJECT_KEY, 0x00 /* type */};

	private BluetoothGattCharacteristic mControlPointCharacteristic;
	private BluetoothGattCharacteristic mPacketCharacteristic;

	private final SecureBluetoothCallback mBluetoothCallback = new SecureBluetoothCallback();

	protected class SecureBluetoothCallback extends BaseCustomBluetoothCallback {

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			if (characteristic.getValue() == null || characteristic.getValue().length < 3) {
				loge("Empty response: " + parse(characteristic));
				mError = DfuBaseService.ERROR_INVALID_RESPONSE;
				notifyLock();
				return;
			}

			final int responseType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

			// The first byte should always be the response code
			if (responseType == OP_CODE_RESPONSE_CODE_KEY) {
				final int requestType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);

				switch (requestType) {
					case OP_CODE_CALCULATE_CHECKSUM_KEY: {
						mProgressInfo.setBytesReceived(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3));
						// TODO check CRC?
						handlePacketReceiptNotification(gatt, characteristic);
						break;
					}
					default: {
						/*
						 * If the DFU target device is in invalid state (e.g. the Init Packet is required but has not been selected), the target will send DFU_STATUS_INVALID_STATE error
						 * for each firmware packet that was send. We are interested may ignore all but the first one.
						 */
						if (mRemoteErrorOccurred)
							break;
						final int status = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 2);
						if (status != DFU_STATUS_SUCCESS)
							mRemoteErrorOccurred = true;

						handleNotification(gatt, characteristic);
						break;
					}
				}
			} else {
				loge("Invalid response: " + parse(characteristic));
				mError = DfuBaseService.ERROR_INVALID_RESPONSE;
			}
			notifyLock();
		}
	}

	SecureDfuImpl(final Intent intent, final DfuBaseService service) {
		super(intent, service);
	}

	@Override
	public boolean isClientCompatible(final Intent intent, final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID);
		if (dfuService == null)
			return false;
		mControlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
		mPacketCharacteristic = dfuService.getCharacteristic(DFU_PACKET_UUID);
		return mControlPointCharacteristic != null && mPacketCharacteristic != null;
	}

	@Override
	public boolean initialize(final Intent intent, final BluetoothGatt gatt, final int fileType, final InputStream firmwareStream, final InputStream initPacketStream) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		if (initPacketStream == null) {
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "The Init packet is required by this version DFU Bootloader");
			mService.terminateConnection(gatt, DfuBaseService.ERROR_INIT_PACKET_REQUIRED);
			return false;
		}

		return super.initialize(intent, gatt, fileType, firmwareStream, initPacketStream);
	}

	@Override
	public BaseBluetoothGattCallback getGattCallback() {
		return mBluetoothCallback;
	}

	@Override
	protected UUID getControlPointCharacteristicUUID() {
		return DFU_CONTROL_POINT_UUID;
	}

	@Override
	protected UUID getPacketCharacteristicUUID() {
		return DFU_PACKET_UUID;
	}

	@Override
	protected UUID getDfuServiceUUID() {
		return DFU_SERVICE_UUID;
	}

	@Override
	public void performDfu(final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		logw("Secure DFU bootloader found");
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_STARTING);

		// Add one second delay to avoid the traffic jam before the DFU mode is enabled
		// Related:
		//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
		mService.waitFor(1000);
		// End

		final BluetoothGatt gatt = mGatt;

		try {
			// Enable notifications
			enableCCCD(mControlPointCharacteristic, NOTIFICATIONS);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Notifications enabled");

			// Wait a second here before going further
			// Related:
			//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
			mService.waitFor(1000);
			// End

			sendInitPacket(gatt);
			sendFirmware(gatt);

			// The device will reset so we don't have to send Disconnect signal.
			mProgressInfo.setProgress(DfuBaseService.PROGRESS_DISCONNECTING);
			mService.waitUntilDisconnected();
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

			// We are ready with DFU, the device is disconnected, let's close it and finalize the operation.
			finalize(intent, false);
		} catch (final UploadAbortedException e) {
			// In secure DFU there is currently not possible to reset the device to application mode, so... do nothing
			// The connection will be terminated in the DfuBaseService
			throw e;
		} catch (final UnknownResponseException e) {
			final int error = DfuBaseService.ERROR_INVALID_RESPONSE;
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, e.getMessage());
			mService.terminateConnection(gatt, error);
		} catch (final RemoteDfuException e) {
			final int error = DfuBaseService.ERROR_REMOTE_MASK | e.getErrorNumber();
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, String.format("Remote DFU error: %s", SecureDfuError.parse(error)));

			// For the Extended Error more details can be obtained on some devices.
			if (e instanceof RemoteDfuExtendedErrorException) {
				final RemoteDfuExtendedErrorException ee = (RemoteDfuExtendedErrorException) e;
				logi("Extended Error details: " + SecureDfuError.parseExtendedError(ee.getExtendedErrorNumber()));
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "Details: " + SecureDfuError.parseExtendedError(ee.getExtendedErrorNumber()) + " (Code = " + ee.getExtendedErrorNumber() + ")");
			}
			mService.terminateConnection(gatt, error);
		}
	}

	/**
	 * This method does the following:
	 * <ol>
	 *     <li>Selects the Command object - this Op Code returns the maximum acceptable size of a command object, and the offset and CRC32 of the command
	 *     that is already stored in the device (in case the DFU was started in a previous connection and disconnected before it has finished).</li>
	 *     <li>If the offset received is greater than 0 and less or equal to the size of the Init file that is to be sent, it will compare the
	 *     received CRC with the local one and, if they match:
	 *     	<ul>
	 *     	    <li>If offset < init file size - it will continue sending the Init file from the point it stopped before,</li>
	 *     	    <li>If offset == init file size - it will send Execute command to execute the Init file, as it may have not been executed before.</li>
	 *     	</ul>
	 *     </li>
	 *     <li>If the CRC don't match, or the received offset is greater then init file size, it creates the Command Object and sends the whole
	 *     Init file as the previous one was different.</li>
	 * </ol>
	 * Sending of the Init packet is done without using PRNs (Packet Receipt Notifications), so they are disabled prior to sending the data.
	 * @param gatt the target GATT device
	 * @throws RemoteDfuException
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws UnknownResponseException
	 */
	private void sendInitPacket(final BluetoothGatt gatt) throws RemoteDfuException, DeviceDisconnectedException, DfuException, UploadAbortedException, UnknownResponseException {
		final CRC32 crc32 = new CRC32(); // Used to calculate CRC32 of the Init packet
		ObjectChecksum checksum;

		// First, select the Command Object. As a response the maximum command size and information whether there is already
		// a command saved from a previous connection is returned.
		logi("Setting object to Command (Op Code = 6, Type = 1)");
		final ObjectInfo info = selectObject(OBJECT_COMMAND);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Command object info received (Max size = %d, Offset = %d, CRC = %08X)", info.maxSize, info.offset, info.CRC32));
		if (mInitPacketSizeInBytes > info.maxSize) {
			// Ignore this. DFU target will send an error if init packet is too large after sending the 'Create object' command
		}

		// Can we resume? If the offset obtained from the device is greater then zero we can compare it with the local init packet CRC
		// and resume sending the init packet, or even skip sending it if the whole file was sent before.
		boolean skipSendingInitPacket = false;
		boolean resumeSendingInitPacket = false;
		if (info.offset > 0 && info.offset <= mInitPacketSizeInBytes) {
			try {
				// Read the same number of bytes from the current init packet to calculate local CRC32
				final byte[] buffer = new byte[info.offset];
				mInitPacketStream.read(buffer);
				// Calculate the CRC32
				crc32.update(buffer);
				final int crc = (int) (crc32.getValue() & 0xFFFFFFFFL);

				if (info.CRC32 == crc) {
					logi("Init packet CRC is the same");
					if (info.offset ==  mInitPacketSizeInBytes) {
						// The whole init packet was sent and it is equal to one we try to send now.
						// There is no need to send it again. We may try to resume sending data.
						logi("-> Whole Init packet was sent before");
						skipSendingInitPacket = true;
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Received CRC match Init packet");
					} else {
						logi("-> " + info.offset + " bytes of Init packet were sent before");
						resumeSendingInitPacket = true;
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Resuming sending Init packet...");
					}
				} else {
					// A different Init packet was sent before, or the error occurred while sending.
					// We have to send the whole Init packet again.
					mInitPacketStream.reset();
					crc32.reset();
				}
			} catch (final IOException e) {
				loge("Error while reading " + info.offset + " bytes from the init packet stream", e);
				try {
					// Go back to the beginning of the stream, we will send the whole init packet
					mInitPacketStream.reset();
					crc32.reset();
				} catch (final IOException e1) {
					loge("Error while resetting the init packet stream", e1);
					mService.terminateConnection(gatt, DfuBaseService.ERROR_FILE_IO_EXCEPTION);
					return;
				}
			}
		}

		if (!skipSendingInitPacket) {
			// The Init packet is sent different way in this implementation than the firmware, and receiving PRNs is not implemented.
			// This value might have been stored on the device, so we have to explicitly disable PRNs.
			logi("Disabling Packet Receipt Notifications (Op Code = 2, Value = 0)");
			setPacketReceiptNotifications(0);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Packet Receipt Notif disabled (Op Code = 2, Value = 0)");

			for (int attempt = 1; attempt <= MAX_ATTEMPTS;) {
				if (!resumeSendingInitPacket) {
					// Create the Init object
					logi("Creating Init packet object (Op Code = 1, Type = 1, Size = " + mInitPacketSizeInBytes + ")");
					writeCreateRequest(OBJECT_COMMAND, mInitPacketSizeInBytes);
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Command object created");
				}
				// Write Init data to the Packet Characteristic
				logi("Sending " + (mInitPacketSizeInBytes - info.offset) + " bytes of init packet...");
				writeInitData(mPacketCharacteristic, crc32);
				final int crc = (int) (crc32.getValue() & 0xFFFFFFFFL);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Command object sent (CRC = %08X)", crc));

				// Calculate Checksum
				logi("Sending Calculate Checksum command (Op Code = 3)");
				checksum = readChecksum();
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));
				logi(String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));

				if (crc == checksum.CRC32) {
					// Everything is OK, we can proceed
					break;
				} else {
					if (attempt < MAX_ATTEMPTS) {
						attempt++;
						logi("CRC does not match! Retrying...(" + attempt + "/" + MAX_ATTEMPTS + ")");
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "CRC does not match! Retrying...(" + attempt + "/" + MAX_ATTEMPTS + ")");
						try {
							// Go back to the beginning, we will send the whole Init packet again
							resumeSendingInitPacket = false;
							info.offset = 0;
							info.CRC32 = 0;
							mInitPacketStream.reset();
							crc32.reset();
						} catch (final IOException e) {
							loge("Error while resetting the init packet stream", e);
							mService.terminateConnection(gatt, DfuBaseService.ERROR_FILE_IO_EXCEPTION);
							return;
						}
					} else {
						loge("CRC does not match!");
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "CRC does not match!");
						mService.terminateConnection(gatt, DfuBaseService.ERROR_CRC_ERROR);
						return;
					}
				}
			}
		}

		// Execute Init packet. It's better to execute it twice than not execute at all...
		logi("Executing init packet (Op Code = 4)");
		writeExecute();
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Command object executed");
	}

	/**
	 * This method does the following:
	 * <ol>
	 *     <li>Sets the Packet Receipt Notification to a value specified in the settings.</li>
	 *     <li>Selects the Data object - this returns maximum single object size and the offset and CRC of the data already saved.</li>
	 *     <li>If the offset received is greater than 0 it will calculate the CRC of the same number of bytes of the firmware to be sent.
	 *     If the CRC match it will continue sending data. Otherwise, it will go back to the beginning of the last chunk, or to the beginning
	 *     of the previous chunk assuming the last one was not executed before, and continue sending data from there.</li>
	 *     <li>If the CRC and offset received match and the offset is equal to the firmware size, it will only send the Execute command.</li>
	 * </ol>
	 * @param gatt the target GATT device
	 * @throws RemoteDfuException
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws UnknownResponseException
	 */
	private void sendFirmware(final BluetoothGatt gatt) throws RemoteDfuException, DeviceDisconnectedException, DfuException, UploadAbortedException, UnknownResponseException {
		// Send the number of packets of firmware before receiving a receipt notification
		final int numberOfPacketsBeforeNotification = mPacketsBeforeNotification;
		if (numberOfPacketsBeforeNotification > 0) {
			logi("Sending the number of packets before notifications (Op Code = 2, Value = " + numberOfPacketsBeforeNotification + ")");
			setPacketReceiptNotifications(numberOfPacketsBeforeNotification);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Packet Receipt Notif Req (Op Code = 2) sent (Value = " + numberOfPacketsBeforeNotification + ")");
		}

		// We are ready to start sending the new firmware.

		logi("Setting object to Data (Op Code = 6, Type = 1)");
		final ObjectInfo info = selectObject(OBJECT_DATA);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Data object info received (Max size = %d, Offset = %d, CRC = %08X)", info.maxSize, info.offset, info.CRC32));
		mProgressInfo.setMaxObjectSizeInBytes(info.maxSize);

		// Number of chunks in which the data will be sent
		final int chunkCount = (mImageSizeInBytes + info.maxSize - 1) / info.maxSize;
		int currentChunk = 0;
		boolean resumeSendingData = false;

		// Can we resume? If the offset obtained from the device is greater then zero we can compare it with the local CRC
		// and resume sending the data.
		if (info.offset > 0) {
			try {
				currentChunk = info.offset / info.maxSize;
				int bytesSentAndExecuted = info.maxSize * currentChunk;
				int bytesSentNotExecuted = info.offset - bytesSentAndExecuted;

				// If the offset is dividable by maxSize, assume that the last page was not executed
				if (bytesSentNotExecuted == 0) {
					bytesSentAndExecuted -= info.maxSize;
					bytesSentNotExecuted = info.maxSize;
				}

				// Read the same number of bytes from the current init packet to calculate local CRC32
				if (bytesSentAndExecuted > 0) {
					mFirmwareStream.read(new byte[bytesSentAndExecuted]); // Read executed bytes
					mFirmwareStream.mark(info.maxSize); // Mark here
				}
				if (bytesSentNotExecuted > 0) {
					// This may be 0 if the whole last chunk was completed before. It may not have been executed and may have CRC error.
					mFirmwareStream.read(new byte[bytesSentNotExecuted]); // Read the rest
				}

				// Calculate the CRC32
				final int crc = (int) (((ArchiveInputStream) mFirmwareStream).getCrc32() & 0xFFFFFFFFL);

				if (crc == info.CRC32) {
					mProgressInfo.setBytesSent(info.offset);
					mProgressInfo.setBytesReceived(info.offset);
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, info.offset + " bytes of data sent before, CRC match");

					// If the whole page was sent and CRC match, we have to make sure it was executed
					if (bytesSentNotExecuted == info.maxSize && info.offset < mImageSizeInBytes) {
						logi("Executing data object (Op Code = 4)");
						writeExecute();
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object executed");
					} else {
						resumeSendingData = true;
					}
				} else {
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, info.offset + " bytes sent before, CRC does not match");
					// The CRC of the current object is not correct. If there was another Data object sent before, its CRC must have been correct,
					// as it has been executed. Either way, we have to create the current object again.
					mProgressInfo.setBytesSent(bytesSentAndExecuted);
					mProgressInfo.setBytesReceived(bytesSentAndExecuted);
					info.offset -= bytesSentNotExecuted;
					info.CRC32 = 0; // invalidate
					mFirmwareStream.reset();
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Resuming from byte " + info.offset + "...");
				}
			} catch (final IOException e) {
				loge("Error while reading firmware stream", e);
				mService.terminateConnection(gatt, DfuBaseService.ERROR_FILE_IO_EXCEPTION);
				return;
			}
		} else {
			// Initialize the timer used to calculate the transfer speed
			mProgressInfo.setBytesSent(0);
		}

		final long startTime = SystemClock.elapsedRealtime();

		if (info.offset < mImageSizeInBytes) {
			int attempt = 1;
			// Each page will be sent in MAX_ATTEMPTS
			while (mProgressInfo.getAvailableObjectSizeIsBytes() > 0) {
				if (!resumeSendingData) {
					// Create the Data object
					logi("Creating Data object (Op Code = 1, Type = 2, Size = " + mProgressInfo.getAvailableObjectSizeIsBytes() + ") (" + (currentChunk + 1) + "/" + chunkCount + ")");
					writeCreateRequest(OBJECT_DATA, mProgressInfo.getAvailableObjectSizeIsBytes());
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object (" + (currentChunk + 1) + "/" + chunkCount + ") created");
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Uploading firmware...");
				} else {
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Resuming uploading firmware...");
					resumeSendingData = false;
				}

				// Send the current object part
				try {
					logi("Uploading firmware...");
					uploadFirmwareImage(mPacketCharacteristic);
				} catch (final DeviceDisconnectedException e) {
					loge("Disconnected while sending data");
					throw e;
				}

				// Calculate Checksum
				logi("Sending Calculate Checksum command (Op Code = 3)");
				final ObjectChecksum checksum = readChecksum();
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));
				logi(String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));

				// Calculate the CRC32
				final int crc = (int) (((ArchiveInputStream) mFirmwareStream).getCrc32() & 0xFFFFFFFFL);
				if (crc == checksum.CRC32) {
					// Execute Init packet
					logi("Executing data object (Op Code = 4)");
					writeExecute();
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object executed");

					// Increment iterator
					currentChunk++;
					attempt = 1;
					//Mark this location after completion of successful transfer.  In the event of a CRC retry on the next packet we will restart from this point.
					mFirmwareStream.mark(0);
				} else {
					String crcFailMessage = String.format(Locale.US, "CRC does not match! Expected %08X but found %08X.", crc, checksum.CRC32);
					if (attempt < MAX_ATTEMPTS) {
						attempt++;
						crcFailMessage += String.format(Locale.US, " Retrying...(%d/%d)", attempt, MAX_ATTEMPTS);
						logi(crcFailMessage);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, crcFailMessage);
						try {
							// Reset the CRC and file pointer back to the previous mark() point after completion of the last successful packet.
							mFirmwareStream.reset();
							mProgressInfo.setBytesSent(checksum.offset - info.maxSize);
						} catch (final IOException e) {
							loge("Error while resetting the firmware stream", e);
							mService.terminateConnection(gatt, DfuBaseService.ERROR_FILE_IO_EXCEPTION);
							return;
						}
					} else {
						loge(crcFailMessage);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, crcFailMessage);
						mService.terminateConnection(gatt, DfuBaseService.ERROR_CRC_ERROR);
						return;
					}
				}
			}
		} else {
			// Looks as if the whole file was sent correctly but has not been executed
			logi("Executing data object (Op Code = 4)");
			writeExecute();
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object executed");
		}

		final long endTime = SystemClock.elapsedRealtime();
		logi("Transfer of " + (mProgressInfo.getBytesSent() - info.offset) + " bytes has taken " + (endTime - startTime) + " ms");
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Upload completed in " + (endTime - startTime) + " ms");
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

	/**
	 * Sets number of data packets that will be send before the notification will be received.
	 *
	 * @param data  control point data packet
	 * @param value number of packets before receiving notification. If this value is 0, then the notification of packet receipt will be disabled by the DFU target.
	 */
	private void setNumberOfPackets(final byte[] data, final int value) {
		data[1] = (byte) (value & 0xFF);
		data[2] = (byte) ((value >> 8) & 0xFF);
	}

	/**
	 * Sets the object size in correct position of the data array.
	 *
	 * @param data  control point data packet
	 * @param value Object size in bytes.
	 */
	private void setObjectSize(final byte[] data, final int value) {
		data[2] = (byte) (value & 0xFF);
		data[3] = (byte) ((value >> 8) & 0xFF);
		data[4] = (byte) ((value >> 16) & 0xFF);
		data[5] = (byte) ((value >> 24) & 0xFF);
	}

	/**
	 * Sets the number of packets that needs to be sent to receive the Packet Receipt Notification. Value 0 disables PRNs.
	 * By default this is disabled. The PRNs may be used to send both the Data and Command object, but this Secure DFU implementation
	 * can handle them only during Data transfer.<br/>
	 * The intention of having PRNs is to make sure the outgoing BLE buffer is not getting overflown. The PRN will be sent after sending all
	 * packets from the queue.
	 *
	 * @param number number of packets required before receiving a Packet Receipt Notification
	 * @throws DfuException
	 * @throws DeviceDisconnectedException
	 * @throws UploadAbortedException
	 * @throws UnknownResponseException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private void setPacketReceiptNotifications(final int number) throws DfuException, DeviceDisconnectedException, UploadAbortedException, UnknownResponseException, RemoteDfuException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read Checksum: device disconnected");

		// Send the number of packets of firmware before receiving a receipt notification
		logi("Sending the number of packets before notifications (Op Code = 2, Value = " + number + ")");
		setNumberOfPackets(OP_CODE_PACKET_RECEIPT_NOTIF_REQ, number);
		writeOpCode(mControlPointCharacteristic, OP_CODE_PACKET_RECEIPT_NOTIF_REQ);

		// Read response
		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY);
		if (status == SecureDfuError.EXTENDED_ERROR)
			throw new RemoteDfuExtendedErrorException("Sending the number of packets failed", response[3]);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Sending the number of packets failed", status);
	}

	/**
	 * Writes the operation code to the characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the device gets disconnected.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param characteristic the characteristic to write to. Should be the DFU CONTROL POINT
	 * @param value          the value to write to the characteristic
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeOpCode(final BluetoothGattCharacteristic characteristic, final byte[] value) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		writeOpCode(characteristic, value, false);
	}

	/**
	 * Writes Create Object request providing the type and size of the object.
	 * @param type {@link #OBJECT_COMMAND} or {@link #OBJECT_DATA}
	 * @param size size of the object or current part of the object
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 * @throws UnknownResponseException
	 */
	private void writeCreateRequest(final int type, final int size) throws DeviceDisconnectedException, DfuException, UploadAbortedException, RemoteDfuException, UnknownResponseException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to create object: device disconnected");

		final byte[] data = (type == OBJECT_COMMAND) ? OP_CODE_CREATE_COMMAND : OP_CODE_CREATE_DATA;
		setObjectSize(data, size);
		writeOpCode(mControlPointCharacteristic, data);

		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_CREATE_KEY);
		if (status == SecureDfuError.EXTENDED_ERROR)
			throw new RemoteDfuExtendedErrorException("Creating Command object failed", response[3]);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Creating Command object failed", status);
	}

	/**
	 * Selects the current object and reads its metadata. The object info contains the max object size, and the offset and CRC32 of the whole object until now.
	 *
	 * @return object info
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private ObjectInfo selectObject(final int type) throws DeviceDisconnectedException, DfuException, UploadAbortedException, RemoteDfuException, UnknownResponseException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read object info: device disconnected");

		OP_CODE_SELECT_OBJECT[1] = (byte) type;
		writeOpCode(mControlPointCharacteristic, OP_CODE_SELECT_OBJECT);

		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_SELECT_OBJECT_KEY);
		if (status == SecureDfuError.EXTENDED_ERROR)
			throw new RemoteDfuExtendedErrorException("Selecting object failed", response[3]);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Selecting object failed", status);

		final ObjectInfo info = new ObjectInfo();
		info.maxSize = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3);
		info.offset = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3 + 4);
		info.CRC32 = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3 + 8);
		return info;
	}

	/**
	 * Sends the Calculate Checksum request. As a response a notification will be sent with current offset and CRC32 of the current object.
	 *
	 * @return requested object info
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private ObjectChecksum readChecksum() throws DeviceDisconnectedException, DfuException, UploadAbortedException, RemoteDfuException, UnknownResponseException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read Checksum: device disconnected");

		writeOpCode(mControlPointCharacteristic, OP_CODE_CALCULATE_CHECKSUM);

		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_CALCULATE_CHECKSUM_KEY);
		if (status == SecureDfuError.EXTENDED_ERROR)
			throw new RemoteDfuExtendedErrorException("Receiving Checksum failed", response[3]);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Receiving Checksum failed", status);

		final ObjectChecksum checksum = new ObjectChecksum();
		checksum.offset = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3);
		checksum.CRC32 = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3 + 4);
		return checksum;
	}

	/**
	 * Sends the Execute operation code and awaits for a return notification containing status code.
	 * The Execute command will confirm the last chunk of data or the last command that was sent.
	 * Creating the same object again, instead of executing it allows to retransmitting it in case of a CRC error.
	 *
	 * @throws DfuException
	 * @throws DeviceDisconnectedException
	 * @throws UploadAbortedException
	 * @throws UnknownResponseException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private void writeExecute() throws DfuException, DeviceDisconnectedException, UploadAbortedException, UnknownResponseException, RemoteDfuException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read Checksum: device disconnected");

		writeOpCode(mControlPointCharacteristic, OP_CODE_EXECUTE);

		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_EXECUTE_KEY);
		if (status == SecureDfuError.EXTENDED_ERROR)
			throw new RemoteDfuExtendedErrorException("Executing object failed", response[3]);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Executing object failed", status);
	}

	private class ObjectInfo extends ObjectChecksum {
		protected int maxSize;
	}

	private class ObjectChecksum {
		protected int offset;
		protected int CRC32;
	}
}
