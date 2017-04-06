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

import java.util.UUID;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.error.LegacyDfuError;

/* package */ class LegacyDfuImpl extends BaseCustomDfuImpl {
	// UUIDs used by the DFU
	protected static final UUID DEFAULT_DFU_SERVICE_UUID       = new UUID(0x000015301212EFDEL, 0x1523785FEABCD123L);
	protected static final UUID DEFAULT_DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEL, 0x1523785FEABCD123L);
	protected static final UUID DEFAULT_DFU_PACKET_UUID        = new UUID(0x000015321212EFDEL, 0x1523785FEABCD123L);
	protected static final UUID DEFAULT_DFU_VERSION_UUID       = new UUID(0x000015341212EFDEL, 0x1523785FEABCD123L);

	protected static UUID DFU_SERVICE_UUID       = DEFAULT_DFU_SERVICE_UUID;
	protected static UUID DFU_CONTROL_POINT_UUID = DEFAULT_DFU_CONTROL_POINT_UUID;
	protected static UUID DFU_PACKET_UUID        = DEFAULT_DFU_PACKET_UUID;
	protected static UUID DFU_VERSION_UUID       = DEFAULT_DFU_VERSION_UUID;

	private static final int DFU_STATUS_SUCCESS = 1;
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
	private static final byte[] OP_CODE_INIT_DFU_PARAMS = new byte[]{OP_CODE_INIT_DFU_PARAMS_KEY}; // SDK 6.0.0 or older
	private static final byte[] OP_CODE_INIT_DFU_PARAMS_START = new byte[]{OP_CODE_INIT_DFU_PARAMS_KEY, 0x00};
	private static final byte[] OP_CODE_INIT_DFU_PARAMS_COMPLETE = new byte[]{OP_CODE_INIT_DFU_PARAMS_KEY, 0x01};
	private static final byte[] OP_CODE_RECEIVE_FIRMWARE_IMAGE = new byte[]{OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY};
	private static final byte[] OP_CODE_VALIDATE = new byte[]{OP_CODE_VALIDATE_KEY};
	private static final byte[] OP_CODE_ACTIVATE_AND_RESET = new byte[]{OP_CODE_ACTIVATE_AND_RESET_KEY};
	private static final byte[] OP_CODE_RESET = new byte[]{OP_CODE_RESET_KEY};
	//private static final byte[] OP_CODE_REPORT_RECEIVED_IMAGE_SIZE = new byte[] { OP_CODE_PACKET_REPORT_RECEIVED_IMAGE_SIZE_KEY };
	private static final byte[] OP_CODE_PACKET_RECEIPT_NOTIF_REQ = new byte[]{OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY, 0x00, 0x00};

	private BluetoothGattCharacteristic mControlPointCharacteristic;
	private BluetoothGattCharacteristic mPacketCharacteristic;

	/**
	 * Flag indicating whether the image size has been already transferred or not.
	 */
	private boolean mImageSizeInProgress;

	private final LegacyBluetoothCallback mBluetoothCallback = new LegacyBluetoothCallback();

	protected class LegacyBluetoothCallback extends BaseCustomBluetoothCallback {
		@Override
		protected void onPacketCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (mImageSizeInProgress) {
				// We've got confirmation that the image size was sent
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
				mImageSizeInProgress = false;
			}
		}

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			final int responseType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

			switch (responseType) {
				case OP_CODE_PACKET_RECEIPT_NOTIF_KEY:
					mProgressInfo.setBytesReceived(characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1));
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

	/* package */ LegacyDfuImpl(final Intent intent, final DfuBaseService service) {
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
	public BaseCustomBluetoothCallback getGattCallback() {
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
		logw("Legacy DFU bootloader found");
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_STARTING);

		// Add one second delay to avoid the traffic jam before the DFU mode is enabled
		// Related:
		//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
		//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
		mService.waitFor(1000);
		// End

		final BluetoothGatt gatt = mGatt;

		/*
		 * DFU Version characteristic has been read by the LegacyButtonlessDfuImpl#isClientCompatible(...) while determining implementation.
		 * No need to read it again.
		 */
		final BluetoothGattCharacteristic versionCharacteristic = gatt.getService(DFU_SERVICE_UUID).getCharacteristic(DFU_VERSION_UUID); // this may be null for older versions of the Bootloader
		final int version = readVersion(versionCharacteristic);

		/*
		 * If the DFU Version characteristic is present and the version returned from it is greater or equal to 0.5, the Extended Init Packet is required.
		 * If the InputStream with init packet is null we may safely abort sending and reset the device as it would happen eventually in few moments.
		 * The DFU target would send DFU INVALID STATE error if the init packet would not be sent before starting file transmission.
		 */
		if (version >= 5 && mInitPacketStream == null) {
			logw("Init packet not set for the DFU Bootloader version " + version);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "The Init packet is required by this version DFU Bootloader");
			mService.terminateConnection(gatt, DfuBaseService.ERROR_INIT_PACKET_REQUIRED);
			return;
		}

		try {
			// Enable notifications
			enableCCCD(mControlPointCharacteristic, NOTIFICATIONS);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Notifications enabled");

			// Wait a second here before going further
			// Related:
			//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
			mService.waitFor(1000);
			// End

			// Set up the temporary variable that will hold the responses
			byte[] response;
			int status;

			/*
			 * The first version of DFU supported only an Application update.
			 * Initializing procedure:
			 * [DFU Start (0x01)] -> DFU Control Point
			 * [App size in bytes (UINT32)] -> DFU Packet
			 * ---------------------------------------------------------------------
			 * Since SDK 6.0 and Soft Device 7.0+ the DFU supports upgrading Soft Device, Bootloader and Application.
			 * Initializing procedure:
			 * [DFU Start (0x01), <Update Mode>] -> DFU Control Point
			 * [SD size in bytes (UINT32), Bootloader size in bytes (UINT32), Application size in bytes (UINT32)] -> DFU Packet
			 * where <Upload Mode> is a bit mask:
			 * 0x01 - Soft Device update
			 * 0x02 - Bootloader update
			 * 0x04 - Application update
			 * so that
			 * 0x03 - Soft Device and Bootloader update
			 * If <Upload Mode> equals 5, 6 or 7 DFU target may return OPERATION_NOT_SUPPORTED [10, 01, 03]. In that case service will try to send
			 * Soft Device and/or Bootloader first, reconnect to the new Bootloader and send the Application in the second connection.
			 * --------------------------------------------------------------------
			 * If DFU target supports only the old DFU, a response [10, 01, 03] will be send as a notification on DFU Control Point characteristic, where:
			 * 10 - Response for...
			 * 01 - DFU Start command
			 * 03 - Operation Not Supported
			 * (see table below)
			 * In that case:
			 * 1. If this is application update - service will try to upload using the old DFU protocol.
			 * 2. In case of SD or BL update an error is returned.
			 */

			// Obtain size of image(s)
			int fileType = mFileType;
			int softDeviceImageSize = (fileType & DfuBaseService.TYPE_SOFT_DEVICE) > 0 ? mImageSizeInBytes : 0;
			int bootloaderImageSize = (fileType & DfuBaseService.TYPE_BOOTLOADER) > 0 ? mImageSizeInBytes : 0;
			int appImageSize = (fileType & DfuBaseService.TYPE_APPLICATION) > 0 ? mImageSizeInBytes : 0;
			// The sizes above may be overwritten if a ZIP file was passed
			if (mFirmwareStream instanceof ArchiveInputStream) {
				final ArchiveInputStream zhis = (ArchiveInputStream) mFirmwareStream;
				if (zhis.isSecureDfuRequired()) {
					loge("Secure DFU is required to upload selected firmware");
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, "The device does not support given firmware.");
					logi("Sending Reset command (Op Code = 6)");
					writeOpCode(mControlPointCharacteristic, OP_CODE_RESET);
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reset request sent");
					mService.terminateConnection(gatt, DfuBaseService.ERROR_FILE_INVALID);
					return;
				}
				softDeviceImageSize = zhis.softDeviceImageSize();
				bootloaderImageSize = zhis.bootloaderImageSize();
				appImageSize = zhis.applicationImageSize();
			}

			boolean extendedInitPacketSupported = true;
			try {
				OP_CODE_START_DFU[1] = (byte) fileType;

				// Send Start DFU command to Control Point
				logi("Sending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
				writeOpCode(mControlPointCharacteristic, OP_CODE_START_DFU);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1, Upload Mode = " + fileType + ")");

				// Send image size in bytes to DFU Packet
				logi("Sending image size array to DFU Packet (" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b)");
				writeImageSize(mPacketCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Firmware image size sent (" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b)");

				// A notification will come with confirmation. Let's wait for it a bit
				response = readNotificationResponse();

				/*
				 * The response received from the DFU device contains:
				 * +---------+--------+----------------------------------------------------+
				 * | byte no | value  | description                                        |
				 * +---------+--------+----------------------------------------------------+
				 * | 0       | 16     | Response code                                      |
				 * | 1       | 1      | The Op Code of a request that this response is for |
				 * | 2       | STATUS | Status code                                        |
				 * +---------+--------+----------------------------------------------------+
				 */
				status = getStatusCode(response, OP_CODE_START_DFU_KEY);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + " Status = " + status + ")");

				// If upload was not completed in the previous connection the INVALID_STATE status will be reported.
				// Theoretically, the connection could be resumed from that point, but there is no guarantee, that the same firmware
				// is to be uploaded now. It's safer to reset the device and start DFU again.
				if (status == LegacyDfuError.INVALID_STATE) {
					resetAndRestart(gatt, intent);
					return;
				}
				if (status != DFU_STATUS_SUCCESS)
					throw new RemoteDfuException("Starting DFU failed", status);
			} catch (final RemoteDfuException e) {
				try {
					if (e.getErrorNumber() != LegacyDfuError.NOT_SUPPORTED)
						throw e;

					// If user wants to send the Soft Device and/or the Bootloader + Application we may try to send the Soft Device/Bootloader files first,
					// and then reconnect and send the application in the second connection.
					if ((fileType & DfuBaseService.TYPE_APPLICATION) > 0 && (fileType & (DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER)) > 0) {
						// Clear the remote error flag
						mRemoteErrorOccurred = false;

						logw("DFU target does not support (SD/BL)+App update");
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "DFU target does not support (SD/BL)+App update");

						fileType &= ~DfuBaseService.TYPE_APPLICATION; // clear application bit
						mFileType = fileType;
						OP_CODE_START_DFU[1] = (byte) fileType;
						mProgressInfo.setTotalPart(2);

						// Set new content type in the ZIP Input Stream and update sizes of images
						final ArchiveInputStream zhis = (ArchiveInputStream) mFirmwareStream;
						zhis.setContentType(fileType);
						appImageSize = 0;

						// Send Start DFU command to Control Point
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Sending only SD/BL");
						logi("Resending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
						writeOpCode(mControlPointCharacteristic, OP_CODE_START_DFU);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1, Upload Mode = " + fileType + ")");

						// Send image size in bytes to DFU Packet
						logi("Sending image size array to DFU Packet: [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");
						writeImageSize(mPacketCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Firmware image size sent [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");

						// A notification will come with confirmation. Let's wait for it a bit
						response = readNotificationResponse();
						status = getStatusCode(response, OP_CODE_START_DFU_KEY);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + " Status = " + status + ")");

						// If upload was not completed in the previous connection the INVALID_STATE status will be reported.
						// Theoretically, the connection could be resumed from that point, but there is no guarantee, that the same firmware
						// is to be uploaded now. It's safer to reset the device and start DFU again.
						if (status == LegacyDfuError.INVALID_STATE) {
							resetAndRestart(gatt, intent);
							return;
						}
						if (status != DFU_STATUS_SUCCESS)
							throw new RemoteDfuException("Starting DFU failed", status);
					} else
						throw e;
				} catch (final RemoteDfuException e1) {
					if (e1.getErrorNumber() != LegacyDfuError.NOT_SUPPORTED)
						throw e1;

					// If operation is not supported by DFU target we may try to upload application with legacy mode, using the old DFU protocol
					if (fileType == DfuBaseService.TYPE_APPLICATION) {
						// Clear the remote error flag
						mRemoteErrorOccurred = false;
						extendedInitPacketSupported = false;

						// The DFU target does not support DFU v.2 protocol
						logw("DFU target does not support DFU v.2");
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "DFU target does not support DFU v.2");

						// Send Start DFU command to Control Point
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Switching to DFU v.1");
						logi("Resending Start DFU command (Op Code = 1)");
						writeOpCode(mControlPointCharacteristic, OP_CODE_START_DFU); // If has 2 bytes, but the second one is ignored
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1)");

						// Send image size in bytes to DFU Packet
						logi("Sending application image size to DFU Packet: " + mImageSizeInBytes + " bytes");
						writeImageSize(mPacketCharacteristic, mImageSizeInBytes);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Firmware image size sent (" + mImageSizeInBytes + " bytes)");

						// A notification will come with confirmation. Let's wait for it a bit
						response = readNotificationResponse();
						status = getStatusCode(response, OP_CODE_START_DFU_KEY);
						mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");

						// If upload was not completed in the previous connection the INVALID_STATE status will be reported.
						// Theoretically, the connection could be resumed from that point, but there is no guarantee, that the same firmware
						// is to be uploaded now. It's safer to reset the device and start DFU again.
						if (status == LegacyDfuError.INVALID_STATE) {
							resetAndRestart(gatt, intent);
							return;
						}
						if (status != DFU_STATUS_SUCCESS)
							throw new RemoteDfuException("Starting DFU failed", status);
					} else
						throw e1;
				}
			}

			/*
			 * If the DFU Version characteristic is present and the version returned from it is greater or equal to 0.5, the Extended Init Packet is required.
			 * For older versions, or if the DFU Version characteristic is not present (pre SDK 7.0.0), the Init Packet (which could have contained only the firmware CRC) was optional.
			 * Deprecated: To calculate the CRC (CRC-CCTII-16 0xFFFF) the following application may be used: http://www.lammertbies.nl/comm/software/index.html -> CRC library.
			 * New: To calculate the CRC (CRC-CCTII-16 0xFFFF) the 'nrf utility' may be used (see below).
			 *
			 * The Init Packet is read from the *.dat file as a binary file. This service you allows to specify the init packet file in two ways.
			 * Since SDK 8.0 and the DFU Library v0.6 using the Distribution packet (ZIP) is recommended. The distribution packet can be created using the
			 * *nrf utility* tool, available together with Master Control Panel v 3.8.0+. See the DFU documentation at http://developer.nordicsemi.com for more details.
			 * An init file may be also provided as a separate file using the {@link #EXTRA_INIT_FILE_PATH} or {@link #EXTRA_INIT_FILE_URI} or in the ZIP file
			 * with the deprecated fixed naming convention:
			 *
			 *    a) If the ZIP file contain a softdevice.hex (or .bin) and/or bootloader.hex (or .bin) the 'system.dat' must also be included.
			 *       In case when both files are present the CRC should be calculated from the two BIN contents merged together.
			 *       This means: if there are softdevice.hex and bootloader.hex files in the ZIP file you have to convert them to BIN
			 *       (e.g. using: http://hex2bin.sourceforge.net/ application), copy them into a single file where the soft device is placed as the first one and calculate
			 *       the CRC for the whole file.
			 *
			 *    b) If the ZIP file contains a application.hex (or .bin) file the 'application.dat' file must be included and contain the Init packet for the application.
			 */
			// Send DFU Init Packet
			if (mInitPacketStream != null) {
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Writing Initialize DFU Parameters...");

				if (extendedInitPacketSupported) {
					logi("Sending the Initialize DFU Parameters START (Op Code = 2, Value = 0)");
					writeOpCode(mControlPointCharacteristic, OP_CODE_INIT_DFU_PARAMS_START);

					logi("Sending " + mInitPacketSizeInBytes + " bytes of init packet");
					writeInitData(mPacketCharacteristic, null);

					logi("Sending the Initialize DFU Parameters COMPLETE (Op Code = 2, Value = 1)");
					writeOpCode(mControlPointCharacteristic, OP_CODE_INIT_DFU_PARAMS_COMPLETE);
					mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Initialize DFU Parameters completed");
				} else {
					// In SDK 4.3 - 6.0.0 the Init Packet could have had only 2 bytes - the CRC16. START-STOP commands were not supported,
					// instead there was just a single command 0x02, followed by a write to DFU Packet after which the device was sending a response.
					logi("Sending the Initialize DFU Parameters (Op Code = 2)");
					writeOpCode(mControlPointCharacteristic, OP_CODE_INIT_DFU_PARAMS);

					logi("Sending " + mInitPacketSizeInBytes + " bytes of init packet");
					writeInitData(mPacketCharacteristic, null);
				}

				// A notification will come with confirmation. Let's wait for it a bit
				response = readNotificationResponse();
				status = getStatusCode(response, OP_CODE_INIT_DFU_PARAMS_KEY);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
				if (status != DFU_STATUS_SUCCESS)
					throw new RemoteDfuException("Device returned error after sending init packet", status);
			}

			// Send the number of packets of firmware before receiving a receipt notification
			// Note: DFU bootloaders from SDK 6.0.0 or older were unable to save incoming data to the flash memory with the same speed
			//       as they are being sent from modern devices, therefore the PRNs are here force-enabled for them.
			//       It has been tested that PRN = 10 may be the highest supported value.
			final int numberOfPacketsBeforeNotification = extendedInitPacketSupported || (mPacketsBeforeNotification > 0 && mPacketsBeforeNotification <= 10) ? mPacketsBeforeNotification : 10;
			if (numberOfPacketsBeforeNotification > 0) {
				logi("Sending the number of packets before notifications (Op Code = 8, Value = " + numberOfPacketsBeforeNotification + ")");
				setNumberOfPackets(OP_CODE_PACKET_RECEIPT_NOTIF_REQ, numberOfPacketsBeforeNotification);
				writeOpCode(mControlPointCharacteristic, OP_CODE_PACKET_RECEIPT_NOTIF_REQ);
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Packet Receipt Notif Req (Op Code = 8) sent (Value = " + numberOfPacketsBeforeNotification + ")");
			}

			// Initialize firmware upload
			logi("Sending Receive Firmware Image request (Op Code = 3)");
			writeOpCode(mControlPointCharacteristic, OP_CODE_RECEIVE_FIRMWARE_IMAGE);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Receive Firmware Image request sent");

			// Send the firmware. The method below sends the first packet and waits until the whole firmware is sent.
			final long startTime = SystemClock.elapsedRealtime();
			mProgressInfo.setBytesSent(0);
			try {
				logi("Uploading firmware...");
				mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Uploading firmware...");
				uploadFirmwareImage(mPacketCharacteristic);
			} catch (final DeviceDisconnectedException e) {
				loge("Disconnected while sending data");
				throw e;
				// TODO reconnect?
			}
			final long endTime = SystemClock.elapsedRealtime();

			// Check the result of the operation
			response = readNotificationResponse();
			status = getStatusCode(response, OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY);
			logi("Response received (Op Code = " + response[0] + ", Req Op Code = " + response[1] + ", Status = " + response[2] + ")");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
			if (status != DFU_STATUS_SUCCESS)
				throw new RemoteDfuException("Device returned error after sending file", status);

			logi("Transfer of " + mProgressInfo.getBytesSent() + " bytes has taken " + (endTime - startTime) + " ms");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Upload completed in " + (endTime - startTime) + " ms");

			// Send Validate request
			logi("Sending Validate request (Op Code = 4)");
			writeOpCode(mControlPointCharacteristic, OP_CODE_VALIDATE);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Validate request sent");

			// A notification will come with status code. Let's wait for it a bit.
			response = readNotificationResponse();
			status = getStatusCode(response, OP_CODE_VALIDATE_KEY);
			logi("Response received (Op Code = " + response[0] + ", Req Op Code = " + response[1] + ", Status = " + response[2] + ")");
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
			if (status != DFU_STATUS_SUCCESS)
				throw new RemoteDfuException("Device returned validation error", status);

			// Send Activate and Reset signal.
			mProgressInfo.setProgress(DfuBaseService.PROGRESS_DISCONNECTING);
			logi("Sending Activate and Reset request (Op Code = 5)");
			writeOpCode(mControlPointCharacteristic, OP_CODE_ACTIVATE_AND_RESET);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Activate and Reset request sent");

			// The device will reset so we don't have to send Disconnect signal.
			mService.waitUntilDisconnected();
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

			// We are ready with DFU, the device is disconnected, let's close it and finalize the operation.

			// In the DFU version 0.5, in case the device is bonded, the target device does not send the Service Changed indication after
			// a jump from bootloader mode to app mode. This issue has been fixed in DFU version 0.6 (SDK 8.0). If the DFU bootloader has been
			// configured to preserve the bond information we do not need to enforce refreshing services, as it will notify the phone using the
			// Service Changed indication.
			finalize(intent, version == 5);
		} catch (final UploadAbortedException e) {
			logi("Sending Reset command (Op Code = 6)");
			mAborted = false; // clear the flag, otherwise the writeOpCode method will not wait until the device disconnects
			writeOpCode(mControlPointCharacteristic, OP_CODE_RESET);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reset request sent");
			// The connection will be terminated in the DfuBaseService
			throw e;
		} catch (final UnknownResponseException e) {
			final int error = DfuBaseService.ERROR_INVALID_RESPONSE;
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, e.getMessage());

			logi("Sending Reset command (Op Code = 6)");
			writeOpCode(mControlPointCharacteristic, OP_CODE_RESET);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reset request sent");
			mService.terminateConnection(gatt, error);
		} catch (final RemoteDfuException e) {
			final int error = DfuBaseService.ERROR_REMOTE_MASK | e.getErrorNumber();
			loge(e.getMessage());
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_ERROR, String.format("Remote DFU error: %s", LegacyDfuError.parse(error)));

			logi("Sending Reset command (Op Code = 6)");
			writeOpCode(mControlPointCharacteristic, OP_CODE_RESET);
			mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reset request sent");
			mService.terminateConnection(gatt, error);
		}
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
			throw new UnknownResponseException("Invalid response received", response, OP_CODE_RESPONSE_CODE_KEY, request);
		return response[2];
	}

	/**
	 * Returns the DFU Version characteristic if such exists. Otherwise it returns 0.
	 *
	 * @param characteristic the characteristic to read
	 * @return a version number or 0 if not present on the bootloader
	 */
	private int readVersion(final BluetoothGattCharacteristic characteristic) {
		// The value of this characteristic has been read before by LegacyButtonlessDfuImpl
		return characteristic != null ? characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0) : 0;
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
		final boolean reset = value[0] == OP_CODE_RESET_KEY || value[0] == OP_CODE_ACTIVATE_AND_RESET_KEY;
		writeOpCode(characteristic, value, reset);
	}

	/**
	 * Writes the image size to the characteristic. This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the device gets disconnected. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param imageSize      the image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGattCharacteristic characteristic, final int imageSize) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		mReceivedData = null;
		mError = 0;
		mImageSizeInProgress = true;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[4]);
		characteristic.setValue(imageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		mGatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeInProgress && mConnected && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Image Size", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to write Image Size: device disconnected");
	}

	/**
	 * <p>
	 * Writes the Soft Device, Bootloader and Application image sizes to the characteristic. Soft Device and Bootloader update is supported since Soft Device s110 v7.0.0.
	 * Sizes of SD, BL and App are uploaded as 3x UINT32 even though some of them may be 0s. F.e. if only App is being updated the data will be <0x00000000, 0x00000000, [App size]>
	 * </p>
	 * <p>
	 * This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the device gets disconnected. If connection state will change, or an error will occur, an exception will be thrown.
	 * </p>
	 *
	 * @param characteristic      the characteristic to write to. Should be the DFU PACKET
	 * @param softDeviceImageSize the Soft Device image size in bytes
	 * @param bootloaderImageSize the Bootloader image size in bytes
	 * @param appImageSize        the Application image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGattCharacteristic characteristic, final int softDeviceImageSize, final int bootloaderImageSize, final int appImageSize)
			throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		mReceivedData = null;
		mError = 0;
		mImageSizeInProgress = true;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[12]);
		characteristic.setValue(softDeviceImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		characteristic.setValue(bootloaderImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 4);
		characteristic.setValue(appImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 8);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		mGatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeInProgress && mConnected && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Image Sizes", mError);
		if (!mConnected)
			throw new DeviceDisconnectedException("Unable to write Image Sizes: device disconnected");
	}

	/**
	 * Sends Reset command to the target device to reset its state and restarts the DFU Service that will start again.
	 * @param gatt the GATT device
	 * @param intent intent used to start the service
	 * @throws DfuException
	 * @throws DeviceDisconnectedException
	 * @throws UploadAbortedException
	 */
	private void resetAndRestart(final BluetoothGatt gatt, final Intent intent) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_WARNING, "Last upload interrupted. Restarting device...");
		// Send 'jump to bootloader command' (Start DFU)
		mProgressInfo.setProgress(DfuBaseService.PROGRESS_DISCONNECTING);
		logi("Sending Reset command (Op Code = 6)");
		writeOpCode(mControlPointCharacteristic, OP_CODE_RESET);
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_APPLICATION, "Reset request sent");

		// The device will reset so we don't have to send Disconnect signal.
		mService.waitUntilDisconnected();
		mService.sendLogBroadcast(DfuBaseService.LOG_LEVEL_INFO, "Disconnected by the remote device");

		final BluetoothGattService gas = gatt.getService(GENERIC_ATTRIBUTE_SERVICE_UUID);
		final boolean hasServiceChanged = gas != null && gas.getCharacteristic(SERVICE_CHANGED_UUID) != null;
		mService.refreshDeviceCache(gatt, !hasServiceChanged);

		// Close the device
		mService.close(gatt);

		logi("Restarting the service");
		final Intent newIntent = new Intent();
		newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
		restartService(newIntent, false);
	}
}
