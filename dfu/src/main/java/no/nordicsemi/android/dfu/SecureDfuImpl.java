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

import java.util.Locale;
import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.error.SecureDfuError;

/* package */ class SecureDfuImpl extends BaseCustomDfuImpl {
	// UUIDs used by the DFU
	protected static final UUID DFU_SERVICE_UUID = new UUID(0x000015301212EFDEL, 0x1523785FEABCD123L); // TODO should be changed later to final UUIDs
	protected static final UUID DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEL, 0x1523785FEABCD123L);
	protected static final UUID DFU_PACKET_UUID = new UUID(0x000015321212EFDEL, 0x1523785FEABCD123L);

	private static final int DFU_STATUS_SUCCESS = 1;

	// Object types
	private static final int OBJECT_COMMAND = 0x01;
	private static final int OBJECT_DATA = 0x02;
	// Operation codes and packets
	private static final int OP_CODE_CREATE_KEY = 0x01;
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY = 0x02;
	private static final int OP_CODE_CALCULATE_CHECKSUM_KEY = 0x03;
	private static final int OP_CODE_EXECUTE_KEY = 0x04;
	private static final int OP_CODE_READ_OBJECT_KEY = 0x05;
	private static final int OP_CODE_READ_OBJECT_INFO_KEY = 0x06;
	private static final int OP_CODE_RESPONSE_CODE_KEY = 0x60;
	private static final byte[] OP_CODE_CREATE_COMMAND = new byte[]{OP_CODE_CREATE_KEY, OBJECT_COMMAND, 0x00, 0x00, 0x00, 0x00 };
	private static final byte[] OP_CODE_CREATE_DATA = new byte[]{OP_CODE_CREATE_KEY, OBJECT_DATA, 0x00, 0x00, 0x00, 0x00};
	private static final byte[] OP_CODE_PACKET_RECEIPT_NOTIF_REQ = new byte[]{OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY, 0x00, 0x00 /* param PRN uint16 in Little Endian */};
	private static final byte[] OP_CODE_CALCULATE_CHECKSUM = new byte[]{OP_CODE_CALCULATE_CHECKSUM_KEY};
	private static final byte[] OP_CODE_EXECUTE = new byte[]{OP_CODE_EXECUTE_KEY};
	private static final byte[] OP_CODE_READ_COMMAND_OBJECT = new byte[]{OP_CODE_READ_OBJECT_KEY, OBJECT_COMMAND};
	private static final byte[] OP_CODE_READ_DATA_OBJECT = new byte[]{OP_CODE_READ_OBJECT_KEY, OBJECT_DATA}; // Reads last error message
	private static final byte[] OP_CODE_READ_INFO = new byte[]{OP_CODE_READ_OBJECT_INFO_KEY, 0x00 /* type */};

	private BluetoothGattCharacteristic mControlPointCharacteristic;
	private BluetoothGattCharacteristic mPacketCharacteristic;
	/** The error details may come in more than one packet. Only the first one has the Response Op Code and status. Others contain just UTF-8 text. */
	private boolean mReceivingErrorDetailsInProgress = false;

	private final SecureBluetoothCallback mBluetoothCallback = new SecureBluetoothCallback();

	protected class SecureBluetoothCallback extends BaseCustomBluetoothCallback {

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			if (mReceivingErrorDetailsInProgress) {
				mReceivingErrorDetailsInProgress = false;
				handleNotification(gatt, characteristic);
				notifyLock();
				return;
			}

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
						logi("PRN, bytes received: " + mProgressInfo.getBytesReceived()); // TODO remove
						// TODO check CRC?
						handlePacketReceiptNotification(gatt, characteristic);
						break;
					}
					default: {
						/*
						 * If the DFU target device is in invalid state (f.e. the Init Packet is required but has not been selected), the target will send DFU_STATUS_INVALID_STATE error
						 * for each firmware packet that was send. We are interested may ignore all but the first one.
						 * After obtaining a remote DFU error the OP_CODE_RESET_KEY will be sent.
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

	SecureDfuImpl(final DfuBaseService service) {
		super(service);
	}

	@Override
	public boolean hasRequiredService(final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID);
		return dfuService != null;
	}

	@Override
	public boolean hasRequiredCharacteristics(final BluetoothGatt gatt) {
		final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID);
		mControlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
		mPacketCharacteristic = dfuService.getCharacteristic(DFU_PACKET_UUID);
		return mControlPointCharacteristic != null && mPacketCharacteristic != null;
	}

	@Override
	protected BaseBluetoothGattCallback getGattCallback() {
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
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_STARTING);

		// Add one second delay to avoid the traffic jam before the DFU mode is enabled
		// Related:
		//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
		mService.waitFor(1000);
		// End

		final BluetoothGatt gatt = mGatt;

		// Enable notifications
		enableCCCD(mControlPointCharacteristic, NOTIFICATIONS);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Notifications enabled");

		// Wait a second here before going further
		// Related:
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
		mService.waitFor(1000);
		// End

		try {
			ObjectChecksum checksum;
			ObjectInfo info;
			byte[] response;
			int status;

			// First, read the Command Object Info. This give information about the maximum command size and whether there is already
			// one saved from a previous connection. It offset and CRC returned are not equal zero, we can compare it with the current init file
			// and skip sending it for the second time.
			logi("Sending Read Command Object Info command (Op Code = 6, Type = 1)");
			info = readObjectInfo(OBJECT_COMMAND);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Command object info received (Max size = %d, Offset = %d, CRC = %08X)", info.maxSize, info.offset, info.CRC32));
			if (mInitPacketSizeInBytes > info.maxSize) {
				// Ignore this. DFU target will send an error if init packet is too large after sending the 'Create object' command
			}

			// The Init packet is sent different way in this implementation than the firmware, and receiving PRNs is not implemented.
			// This value might have been stored on the device, so we have to explicitly disable PRNs.
			logi("Disabling Packet Receipt Notifications (Op Code = 2, Value = 0)");
			setPacketReceiptNotifications(0);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Packet Receipt Notif disabled (Op Code = 2, Value = 0)");

			// TODO resume uploading

			// Create the Init object
			logi("Creating Init packet object (Op Code = 1, Type = 1, Size = " + mInitPacketSizeInBytes + ")");
			writeCreateRequest(OBJECT_COMMAND, mInitPacketSizeInBytes);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Command object created");

			// Write Init data to the Packet Characteristic
			logi("Sending " + mInitPacketSizeInBytes + " bytes of init packet...");
			writeInitData(mPacketCharacteristic);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Command object sent");

			// Calculate Checksum
			logi("Sending Calculate Checksum command (Op Code = 3)");
			checksum = readChecksum();
			// TODO validate
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));
			logi(String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));

			// Execute Init packet
			logi("Executing init packet (Op Code = 4)");
			writeExecute();
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Command object executed");

			// Send the number of packets of firmware before receiving a receipt notification
			final int numberOfPacketsBeforeNotification = mPacketsBeforeNotification;
			if (numberOfPacketsBeforeNotification > 0) {
				logi("Sending the number of packets before notifications (Op Code = 2, Value = " + numberOfPacketsBeforeNotification + ")");
				setPacketReceiptNotifications(numberOfPacketsBeforeNotification);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Packet Receipt Notif Req (Op Code = 2) sent (Value = " + numberOfPacketsBeforeNotification + ")");
			}

			logi("Sending Read Data Object Info command (Op Code = 6, Type = 2)");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reading data object info...");
			info = readObjectInfo(OBJECT_DATA);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Data object info received (Max size = %d, Offset = %d, CRC = %08X)", info.maxSize, info.offset, info.CRC32));
			mProgressInfo.setMaxObjectSizeInBytes(info.maxSize);
			mProgressInfo.setBytesSent(0);

			// TODO resume?

			// Number of chunks in which the data will be sent
			final int count = (mImageSizeInBytes + info.maxSize - 1) / info.maxSize;
			// Chunk iterator
			int i = 1;
			final long startTime = SystemClock.elapsedRealtime();
			while (mProgressInfo.getAvailableObjectSizeIsBytes() > 0) {
				// Create the Data object
				logi("Creating Data object (Op Code = 1, Type = 2, Size = " + mProgressInfo.getAvailableObjectSizeIsBytes() + ") (" + i + "/" + count + ")");
				writeCreateRequest(OBJECT_DATA, mProgressInfo.getAvailableObjectSizeIsBytes());
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object (" + i + "/" + count + ") created");

				// Send the current object part
				try {
					logi("Uploading firmware...");
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Uploading firmware...");
					uploadFirmwareImage(mPacketCharacteristic);
				} catch (final DeviceDisconnectedException e) {
					loge("Disconnected while sending data");
					throw e;
				}

				// Calculate Checksum
				logi("Sending Calculate Checksum command (Op Code = 3)");
				checksum = readChecksum();
				// TODO validate
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));
				logi(String.format(Locale.US, "Checksum received (Offset = %d, CRC = %08X)", checksum.offset, checksum.CRC32));

				// Execute Init packet
				logi("Executing data object (Op Code = 4)");
				writeExecute();
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Data object executed");

				// Increment iterator
				i++;
			}
			final long endTime = SystemClock.elapsedRealtime();
			logi("Transfer of " + mProgressInfo.getBytesSent() + " bytes has taken " + (endTime - startTime) + " ms");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Upload completed in " + (endTime - startTime) + " ms");

			// The device will reset so we don't have to send Disconnect signal.
			mProgressInfo.setProgress(DfuBaseService.PROGRESS_DISCONNECTING);
			mService.waitUntilDisconnected();
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

			// We are ready with DFU, the device is disconnected, let's close it and finalize the operation.
			finalize(intent, false);
		} catch (final UnknownResponseException e) {
			final int error = DfuBaseService.ERROR_INVALID_RESPONSE;
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, e.getMessage());
			mService.terminateConnection(gatt, error);
		} catch (final RemoteDfuException e) {
			final int error = DfuBaseService.ERROR_REMOTE_MASK | e.getErrorNumber();
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, String.format("Remote DFU error: %s", SecureDfuError.parse(error)));

			try {
				final ErrorMessage details = readLastError();
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "Details: " + details.message + " (Code = " + details.code + ")");
				logi("Error details: " + details.message + " (Code = " + details.code + ")");
			} catch (final Exception e1) {
				loge("Reading error details failed", e1);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Reading error details failed");
			}
			mService.terminateConnection(gatt, error);
		}
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
		if (response == null || response.length < 3 || response[0] != OP_CODE_RESPONSE_CODE_KEY || response[1] != request || response[2] < SecureDfuError.INVALID_CODE || response[2] > 10)
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
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Creating Command object failed", status);
	}

	/**
	 * Reads the last error message from the device.
	 *
	 * @return the error details
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private ErrorMessage readLastError() throws DeviceDisconnectedException, DfuException, UploadAbortedException, RemoteDfuException, UnknownResponseException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read object info: device disconnected");

		final BluetoothGattCharacteristic characteristic = mControlPointCharacteristic;
		writeOpCode(characteristic, OP_CODE_READ_DATA_OBJECT);

		mReceivingErrorDetailsInProgress = true;
		readNotificationResponse(); // ignore the result, the value is in mControlPointCharacteristic

		final int code = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 2);
		int length = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 4);
		final StringBuilder builder = new StringBuilder();
		builder.append(characteristic.getStringValue(6));
		while (length - builder.length() > 0) {
			mReceivedData = null;
			mReceivingErrorDetailsInProgress = true;
			readNotificationResponse();
			builder.append(characteristic.getStringValue(0));

			// Finish if byte 0 is received at the end
			if (characteristic.getValue()[characteristic.getValue().length - 1] == 0)
				break;
		}

		final ErrorMessage error = new ErrorMessage();
		error.code = code;
		error.message = builder.toString();
		return error;
	}

	/**
	 * Reads the Object Info for object with given type. The object info contains the max object size, the last offset and CRC32 of the whole object until now.
	 *
	 * @return requested object info
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 * @throws RemoteDfuException thrown when the returned status code is not equal to {@link #DFU_STATUS_SUCCESS}
	 */
	private ObjectInfo readObjectInfo(final int type) throws DeviceDisconnectedException, DfuException, UploadAbortedException, RemoteDfuException, UnknownResponseException {
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to read object info: device disconnected");

		OP_CODE_READ_INFO[1] = (byte) type;
		writeOpCode(mControlPointCharacteristic, OP_CODE_READ_INFO);

		final byte[] response = readNotificationResponse();
		final int status = getStatusCode(response, OP_CODE_READ_OBJECT_INFO_KEY);
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Reading Object Info failed", status);

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
		if (status != DFU_STATUS_SUCCESS)
			throw new RemoteDfuException("Receiving Checksum failed", status);

		final ObjectChecksum checksum = new ObjectChecksum();
		checksum.offset = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3);
		checksum.CRC32 = mControlPointCharacteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 3 + 4);
//		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, String.format(Locale.US, "Response received (Op Code = %d, Status = %d, Offset = %d, CRC = %08X)", response[1], status, checksum.offset, checksum.CRC32));
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

	private class ErrorMessage {
		protected int code;
		protected String message;
	}
}
