/*
 * Copyright (c) 2018, Nordic Semiconductor
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
 */

package no.nordicsemi.android.error;

import android.bluetooth.BluetoothGatt;

import no.nordicsemi.android.dfu.DfuBaseService;

/**
 * Parses the error numbers according to the <b>gatt_api.h</b> file from bluedroid stack.
 * See: https://android.googlesource.com/platform/external/bluetooth/bluedroid/+/android-5.1.0_r1/stack/include/gatt_api.h (and other versions) for details.
 * See also: https://android.googlesource.com/platform/external/libnfc-nci/+/master/src/include/hcidefs.h#447 for other possible HCI errors.
 */
public class GattError {
	// Starts at line 106 of gatt_api.h file
	/**
	 * Converts the connection status given by the {@link android.bluetooth.BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} to error name.
	 * @param error the status number
	 * @return the error name as stated in the gatt_api.h file
	 */
	public static String parseConnectionError(final int error) {
		return switch (error) {
			case BluetoothGatt.GATT_SUCCESS -> "SUCCESS";
			case 0x01 -> "GATT CONN L2C FAILURE";
			case 0x08 -> "GATT CONN TIMEOUT";
			case 0x13 -> "GATT CONN TERMINATE PEER USER";
			case 0x16 -> "GATT CONN TERMINATE LOCAL HOST";
			case 0x3E -> "GATT CONN FAIL ESTABLISH";
			case 0x22 -> "GATT CONN LMP TIMEOUT";
			case 0x0100 -> "GATT CONN CANCEL ";
			case 0x0085 -> "GATT ERROR"; // Device not reachable
			default -> "UNKNOWN (" + error + ")";
		};
	}

	// Starts at line 29 of the gatt_api.h file
	/**
	 * Converts the bluetooth communication status given by other BluetoothGattCallbacks to error name. It also parses the DFU errors.
	 * @param error the status number
	 * @return the error name as stated in the gatt_api.h file
	 */
	public static String parse(final int error) {
		return switch (error) {
			case 0x0001 -> "GATT INVALID HANDLE";
			case 0x0002 -> "GATT READ NOT PERMIT";
			case 0x0003 -> "GATT WRITE NOT PERMIT";
			case 0x0004 -> "GATT INVALID PDU";
			case 0x0005 -> "GATT INSUF AUTHENTICATION";
			case 0x0006 -> "GATT REQ NOT SUPPORTED";
			case 0x0007 -> "GATT INVALID OFFSET";
			case 0x0008 -> "GATT INSUF AUTHORIZATION";
			case 0x0009 -> "GATT PREPARE Q FULL";
			case 0x000a -> "GATT NOT FOUND";
			case 0x000b -> "GATT NOT LONG";
			case 0x000c -> "GATT INSUF KEY SIZE";
			case 0x000d -> "GATT INVALID ATTR LEN";
			case 0x000e -> "GATT ERR UNLIKELY";
			case 0x000f -> "GATT INSUF ENCRYPTION";
			case 0x0010 -> "GATT UNSUPPORT GRP TYPE";
			case 0x0011 -> "GATT INSUF RESOURCE";
			case 0x001A -> "HCI ERROR UNSUPPORTED REMOTE FEATURE";
			case 0x001E -> "HCI ERROR INVALID LMP PARAM";
			case 0x0022 -> "GATT CONN LMP TIMEOUT";
			case 0x002A -> "HCI ERROR DIFF TRANSACTION COLLISION";
			case 0x003A -> "GATT CONTROLLER BUSY";
			case 0x003B -> "GATT UNACCEPT CONN INTERVAL";
			case 0x0087 -> "GATT ILLEGAL PARAMETER";
			case 0x0080 -> "GATT NO RESOURCES";
			case 0x0081 -> "GATT INTERNAL ERROR";
			case 0x0082 -> "GATT WRONG STATE";
			case 0x0083 -> "GATT DB FULL";
			case 0x0084 -> "GATT BUSY";
			case 0x0085 -> "GATT ERROR";
			case 0x0086 -> "GATT CMD STARTED";
			case 0x0088 -> "GATT PENDING";
			case 0x0089 -> "GATT AUTH FAIL";
			case 0x008a -> "GATT MORE";
			case 0x008b -> "GATT INVALID CFG";
			case 0x008c -> "GATT SERVICE STARTED";
			case 0x008d -> "GATT ENCRYPTED NO MITM";
			case 0x008e -> "GATT NOT ENCRYPTED";
			case 0x008f -> "GATT CONGESTED";
			case 0x00FD -> "GATT CCCD CFG ERROR";
			case 0x00FE -> "GATT PROCEDURE IN PROGRESS";
			case 0x00FF -> "GATT VALUE OUT OF RANGE";
			case 0x0101 -> "TOO MANY OPEN CONNECTIONS";
			case DfuBaseService.ERROR_DEVICE_DISCONNECTED -> "DFU DEVICE DISCONNECTED";
			case DfuBaseService.ERROR_FILE_NOT_FOUND -> "DFU FILE NOT FOUND";
			case DfuBaseService.ERROR_FILE_ERROR -> "DFU FILE ERROR";
			case DfuBaseService.ERROR_FILE_INVALID -> "DFU NOT A VALID HEX FILE";
			case DfuBaseService.ERROR_FILE_IO_EXCEPTION -> "DFU IO EXCEPTION";
			case DfuBaseService.ERROR_SERVICE_DISCOVERY_NOT_STARTED ->
					"DFU SERVICE DISCOVERY NOT STARTED";
			case DfuBaseService.ERROR_SERVICE_NOT_FOUND -> "DFU CHARACTERISTICS NOT FOUND";
			case DfuBaseService.ERROR_INVALID_RESPONSE -> "DFU INVALID RESPONSE";
			case DfuBaseService.ERROR_FILE_TYPE_UNSUPPORTED -> "DFU FILE TYPE NOT SUPPORTED";
			case DfuBaseService.ERROR_BLUETOOTH_DISABLED -> "BLUETOOTH ADAPTER DISABLED";
			case DfuBaseService.ERROR_INIT_PACKET_REQUIRED -> "DFU INIT PACKET REQUIRED";
			case DfuBaseService.ERROR_FILE_SIZE_INVALID -> "DFU INIT PACKET REQUIRED";
			case DfuBaseService.ERROR_CRC_ERROR -> "DFU CRC ERROR";
			case DfuBaseService.ERROR_DEVICE_NOT_BONDED -> "DFU DEVICE NOT BONDED";
			default -> "UNKNOWN (" + error + ")";
		};
	}

	public static String parseDfuRemoteError(final int error) {
		return switch (error & (DfuBaseService.ERROR_REMOTE_TYPE_LEGACY | DfuBaseService.ERROR_REMOTE_TYPE_SECURE | DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS)) {
			case DfuBaseService.ERROR_REMOTE_TYPE_LEGACY -> LegacyDfuError.parse(error);
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE -> SecureDfuError.parse(error);
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED ->
					SecureDfuError.parseExtendedError(error);
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS ->
					SecureDfuError.parseButtonlessError(error);
			default -> "UNKNOWN (" + error + ")";
		};
	}
}
