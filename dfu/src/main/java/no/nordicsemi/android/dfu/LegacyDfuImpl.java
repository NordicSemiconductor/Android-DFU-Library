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
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;

import java.io.InputStream;
import java.util.UUID;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ class LegacyDfuImpl extends BaseCustomDfuImpl {
	// DFU status values
	public static final int DFU_STATUS_SUCCESS = 1;
	public static final int DFU_STATUS_INVALID_STATE = 2;
	public static final int DFU_STATUS_NOT_SUPPORTED = 3;
	public static final int DFU_STATUS_DATA_SIZE_EXCEEDS_LIMIT = 4;
	public static final int DFU_STATUS_CRC_ERROR = 5;
	public static final int DFU_STATUS_OPERATION_FAILED = 6;
	// Operation codes and packets
	private static final int OP_CODE_START_DFU_KEY = 0x01; // 1
	private static final int OP_CODE_INIT_DFU_PARAMS_KEY = 0x02; // 2
	private static final int OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY = 0x03; // 3
	private static final int OP_CODE_VALIDATE_KEY = 0x04; // 4
	private static final int OP_CODE_ACTIVATE_AND_RESET_KEY = 0x05; // 5
	private static final int OP_CODE_RESET_KEY = 0x06; // 6
	//private static final int OP_CODE_PACKET_REPORT_RECEIVED_IMAGE_SIZE_KEY = 0x07; // 7
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY = 0x08; // 8
	private static final int OP_CODE_RESPONSE_CODE_KEY = 0x10; // 16
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_KEY = 0x11; // 11
	private static final byte[] OP_CODE_START_DFU = new byte[]{OP_CODE_START_DFU_KEY, 0x00};
	private static final byte[] OP_CODE_INIT_DFU_PARAMS_START = new byte[]{OP_CODE_INIT_DFU_PARAMS_KEY, 0x00};
	private static final byte[] OP_CODE_INIT_DFU_PARAMS_COMPLETE = new byte[]{OP_CODE_INIT_DFU_PARAMS_KEY, 0x01};
	private static final byte[] OP_CODE_RECEIVE_FIRMWARE_IMAGE = new byte[]{OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY};
	private static final byte[] OP_CODE_VALIDATE = new byte[]{OP_CODE_VALIDATE_KEY};
	private static final byte[] OP_CODE_ACTIVATE_AND_RESET = new byte[]{OP_CODE_ACTIVATE_AND_RESET_KEY};
	private static final byte[] OP_CODE_RESET = new byte[]{OP_CODE_RESET_KEY};
	//private static final byte[] OP_CODE_REPORT_RECEIVED_IMAGE_SIZE = new byte[] { OP_CODE_PACKET_REPORT_RECEIVED_IMAGE_SIZE_KEY };
	private static final byte[] OP_CODE_PACKET_RECEIPT_NOTIF_REQ = new byte[]{OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY, 0x00, 0x00};

	// UUIDs used by the DFU
	private static final UUID DFU_SERVICE_UUID = new UUID(0x000015301212EFDEL, 0x1523785FEABCD123L);
	private static final UUID DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEL, 0x1523785FEABCD123L);
	private static final UUID DFU_PACKET_UUID = new UUID(0x000015321212EFDEL, 0x1523785FEABCD123L);
	private static final UUID DFU_VERSION = new UUID(0x000015341212EFDEL, 0x1523785FEABCD123L);
	private int mFileType;
	/**
	 * Flag indicating whether the image size has been already transferred or not
	 */
	private boolean mImageSizeSent;

	protected class LegacyBluetoothCallback extends BaseCustomBluetoothCallback {
		@Override
		protected void onPacketCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (mImageSizeSent) {
				// We've got confirmation that the image size was sent
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
				mImageSizeSent = false;
			}
		}

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			final int responseType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

			switch (responseType) {
				case OP_CODE_PACKET_RECEIPT_NOTIF_KEY:
					mProgressInfo.setBytesReceived(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1));
					mService.updateProgressNotification(mProgressInfo);
					handlePacketReceiptNotification(gatt, characteristic);
					break;
				case OP_CODE_RESPONSE_CODE_KEY:
				default:
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
			notifyLock();
		}
	}

	LegacyDfuImpl(final DfuBaseService service, final InputStream firmwareStream, final InputStream initPacketStream, final int packetsBeforeNotification) {
		super(service, firmwareStream, initPacketStream, packetsBeforeNotification);
	}

	@Override
	protected BluetoothGattCallback getGattCallback() {
		return new LegacyBluetoothCallback();
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
	 * Checks whether the response received is valid and returns the status code.
	 *
	 * @param response the response received from the DFU device.
	 * @param request  the expected Op Code
	 * @return the status code
	 * @throws UnknownResponseException if response was not valid
	 */
	private int getStatusCode(final byte[] response, final int request) throws UnknownResponseException {
		if (response == null || response.length != 3 || response[0] != OP_CODE_RESPONSE_CODE_KEY || response[1] != request || response[2] < 1 || response[2] > 6)
			throw new UnknownResponseException("Invalid response received", response, request);
		return response[2];
	}

	/**
	 * Reads the DFU Version characteristic if such exists. Otherwise it returns 0.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to read
	 * @return a version number or 0 if not present on the bootloader
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private int readVersion(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read version number", mConnectionState);
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
				while (((!mRequestCompleted || characteristic.getValue() == null ) && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused) {
					mRequestCompleted = false;
					mLock.wait();
				}
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to read version number", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read version number", mConnectionState);

		// The version is a 16-bit unsigned int
		return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
	}

	/**
	 * Writes the operation code to the characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)} will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to write to. Should be the DFU CONTROL POINT
	 * @param value          the value to write to the characteristic
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeOpCode(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		final boolean reset = value[0] == OP_CODE_RESET_KEY || value[0] == OP_CODE_ACTIVATE_AND_RESET_KEY;
		writeOpCode(gatt, characteristic, value, reset);
	}

	/**
	 * Writes the image size to the characteristic. This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param imageSize      the image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int imageSize) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		mReceivedData = null;
		mError = 0;
		mImageSizeSent = true;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[4]);
		characteristic.setValue(imageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeSent && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Image Size", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Image Size", mConnectionState);
	}

	/**
	 * <p>
	 * Writes the Soft Device, Bootloader and Application image sizes to the characteristic. Soft Device and Bootloader update is supported since Soft Device s110 v7.0.0.
	 * Sizes of SD, BL and App are uploaded as 3x UINT32 even though some of them may be 0s. F.e. if only App is being updated the data will be <0x00000000, 0x00000000, [App size]>
	 * </p>
	 * <p>
	 * This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)} will be called or the connection state will
	 * change from {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 * </p>
	 *
	 * @param gatt                the GATT device
	 * @param characteristic      the characteristic to write to. Should be the DFU PACKET
	 * @param softDeviceImageSize the Soft Device image size in bytes
	 * @param bootloaderImageSize the Bootloader image size in bytes
	 * @param appImageSize        the Application image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int softDeviceImageSize, final int bootloaderImageSize, final int appImageSize)
			throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		mReceivedData = null;
		mError = 0;
		mImageSizeSent = true;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[12]);
		characteristic.setValue(softDeviceImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		characteristic.setValue(bootloaderImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 4);
		characteristic.setValue(appImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 8);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeSent && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Image Sizes", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Image Sizes", mConnectionState);
	}
}
