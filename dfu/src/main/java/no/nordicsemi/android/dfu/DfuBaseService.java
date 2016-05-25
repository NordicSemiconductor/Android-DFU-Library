/*************************************************************************************************************************************************
 * Copyright (c) 2015, Nordic Semiconductor
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

import android.app.Activity;
import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.HexInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.internal.exception.SizeValidationException;
import no.nordicsemi.android.dfu.internal.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.dfu.internal.scanner.BootloaderScannerFactory;
import no.nordicsemi.android.error.GattError;

/**
 * The DFU Service provides full support for Over-the-Air (OTA) Device Firmware Update (DFU) by Nordic Semiconductor.
 * With the Soft Device 7.0.0+ it allows to upload a new Soft Device, new Bootloader and a new Application. For older soft devices only the Application update is supported.
 * <p>
 * To run the service to your application extend it in your project and overwrite the missing method. Remember to add your class to the AndroidManifest.xml file.
 * </p>
 * <p>
 * The {@link DfuServiceInitiator} object should be used to start the DFU Service.
 * <p/>
 * <pre>
 * final DfuServiceInitiator starter = new DfuServiceInitiator(mSelectedDevice.getAddress())
 * 		.setDeviceName(mSelectedDevice.getName())
 * 		.setKeepBond(keepBond)
 * 		.setZip(mFileStreamUri, mFilePath)
 *		.start(this, DfuService.class);
 * </pre>
 * <p>
 *     You may register the progress and log listeners using the {@link DfuServiceListenerHelper} helper class. See {@link DfuProgressListener} and {@link DfuLogListener} for
 *     more information.
 * </p>
 * <p>
 * The service will show its progress on the notification bar and will send local broadcasts to the application.
 * </p>
 */
public abstract class DfuBaseService extends IntentService {
	private static final String TAG = "DfuBaseService";

	public static final int NOTIFICATION_ID = 283; // a random number

	/**
	 * The address of the device to update.
	 */
	public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_ADDRESS";
	/**
	 * The optional device name. This name will be shown in the notification.
	 */
	public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_NAME";
	/**
	 * A boolean indicating whether to disable the progress notification in the status bar. Defaults to false.
	 */
	public static final String EXTRA_DISABLE_NOTIFICATION = "no.nordicsemi.android.dfu.extra.EXTRA_DISABLE_NOTIFICATION";
	/**
	 * <p>
	 * If the new firmware (application) does not share the bond information with the old one, the bond information is lost. Set this flag to <code>true</code>
	 * to make the service create new bond with the new application when the upload is done (and remove the old one). When set to <code>false</code> (default),
	 * the DFU service assumes that the LTK is shared between them. Note: currently it is not possible to remove the old bond without creating a new one so if
	 * your old application supported bonding while the new one does not you have to modify the source code yourself.
	 * </p>
	 * <p>
	 * In case of updating the soft device the application is always removed together with the bond information.
	 * </p>
	 * <p>
	 * Search for occurrences of EXTRA_RESTORE_BOND in this file to check the implementation and get more details.
	 * </p>
	 */
	public static final String EXTRA_RESTORE_BOND = "no.nordicsemi.android.dfu.extra.EXTRA_RESTORE_BOND";
	/**
	 * <p>This flag indicated whether the bond information should be kept or removed after an upgrade of the Application.
	 * If an application is being updated on a bonded device with the DFU Bootloader that has been configured to preserve the bond information for the new application,
	 * set it to <code>true</code>.</p>
	 *
	 * <p>By default the DFU Bootloader clears the whole application's memory. It may be however configured in the \Nordic\nrf51\components\libraries\bootloader_dfu\dfu_types.h
	 * file (sdk 11, line 76: <code>#define DFU_APP_DATA_RESERVED 0x0000</code>) to preserve some pages. The BLE_APP_HRM_DFU sample app stores the LTK and System Attributes in the first
	 * two pages, so in order to preserve the bond information this value should be changed to 0x0800 or more.
	 * When those data are preserved, the new Application will notify the app with the Service Changed indication when launched for the first time. Otherwise this
	 * service will remove the bond information from the phone and force to refresh the device cache (see {@link #refreshDeviceCache(android.bluetooth.BluetoothGatt, boolean)}).</p>
	 *
	 * <p>In contrast to {@link #EXTRA_RESTORE_BOND} this flag will not remove the old bonding and recreate a new one, but will keep the bond information untouched.</p>
	 * <p>The default value of this flag is <code>false</code></p>
	 */
	public static final String EXTRA_KEEP_BOND = "no.nordicsemi.android.dfu.extra.EXTRA_KEEP_BOND";
	/**
	 * A path to the file with the new firmware. It may point to a HEX, BIN or a ZIP file.
	 * Some file manager applications return the path as a String while other return a Uri. Use the {@link #EXTRA_FILE_URI} in the later case.
	 * For files included in /res/raw resource directory please use {@link #EXTRA_FILE_RES_ID} instead.
	 */
	public static final String EXTRA_FILE_PATH = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_PATH";
	/**
	 * See {@link #EXTRA_FILE_PATH} for details.
	 */
	public static final String EXTRA_FILE_URI = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_URI";
	/**
	 * See {@link #EXTRA_FILE_PATH} for details.
	 */
	public static final String EXTRA_FILE_RES_ID = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_RES_ID";
	/**
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+). Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_PATH = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_PATH";
	/**
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+). Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_URI = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_URI";
	/**
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+). Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_RES_ID = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_RES_ID";
	/**
	 * The input file mime-type. Currently only "application/zip" (ZIP) or "application/octet-stream" (HEX or BIN) are supported. If this parameter is
	 * empty the "application/octet-stream" is assumed.
	 */
	public static final String EXTRA_FILE_MIME_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_MIME_TYPE";
	// Since the DFU Library version 0.5 both HEX and BIN files are supported. As both files have the same MIME TYPE the distinction is made based on the file extension.
	public static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";
	public static final String MIME_TYPE_ZIP = "application/zip";
	/**
	 * This optional extra parameter may contain a file type. Currently supported are:
	 * <ul>
	 * <li>{@link #TYPE_SOFT_DEVICE} - only Soft Device update</li>
	 * <li>{@link #TYPE_BOOTLOADER} - only Bootloader update</li>
	 * <li>{@link #TYPE_APPLICATION} - only application update</li>
	 * <li>{@link #TYPE_AUTO} - the file is a ZIP file that may contain more than one HEX/BIN + DAT files. Since SDK 8.0 the ZIP Distribution packet is a recommended
	 * way of delivering firmware files. Please, see the DFU documentation for more details. A ZIP distribution packet may be created using the 'nrf utility'
	 * command line application, that is a part of Master Control Panel 3.8.0.The ZIP file MAY contain only the following files:
	 * <b>softdevice.hex/bin</b>, <b>bootloader.hex/bin</b>, <b>application.hex/bin</b> to determine the type based on its name. At lease one of them MUST be present.
	 * </li>
	 * </ul>
	 * If this parameter is not provided the type is assumed as follows:
	 * <ol>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is <code>null</code> or is equal to {@value #MIME_TYPE_OCTET_STREAM} - the {@link #TYPE_APPLICATION} is assumed.</li>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is equal to {@value #MIME_TYPE_ZIP} - the {@link #TYPE_AUTO} is assumed.</li>
	 * </ol>
	 */
	public static final String EXTRA_FILE_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_TYPE";
	/**
	 * <p>
	 * The file contains a new version of Soft Device.
	 * </p>
	 * <p>
	 * Since DFU Library 7.0 all firmware may contain an Init packet. The Init packet is required if Extended Init Packet is used by the DFU bootloader (SDK 7.0+)..
	 * The Init packet for the bootloader must be placed in the .dat file.
	 * </p>
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_SOFT_DEVICE = 0x01;
	/**
	 * <p>
	 * The file contains a new version of Bootloader.
	 * </p>
	 * <p>
	 * Since DFU Library 7.0 all firmware may contain an Init packet. The Init packet is required if Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * The Init packet for the bootloader must be placed in the .dat file.
	 * </p>
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_BOOTLOADER = 0x02;
	/**
	 * <p>
	 * The file contains a new version of Application.
	 * </p>
	 * <p>
	 * Since DFU Library 0.5 all firmware may contain an Init packet. The Init packet is required if Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * The Init packet for the application must be placed in the .dat file.
	 * </p>
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_APPLICATION = 0x04;
	/**
	 * <p>
	 * A ZIP file that consists of more than 1 file. Since SDK 8.0 the ZIP Distribution packet is a recommended way of delivering firmware files. Please, see the DFU documentation for
	 * more details. A ZIP distribution packet may be created using the 'nrf utility' command line application, that is a part of Master Control Panel 3.8.0.
	 * For backwards compatibility this library supports also ZIP files without the manifest file. Instead they must follow the fixed naming convention:
	 * The names of files in the ZIP must be: <b>softdevice.hex</b> (or .bin), <b>bootloader.hex</b> (or .bin), <b>application.hex</b> (or .bin) in order
	 * to be read correctly. Using the Soft Device v7.0.0+ the Soft Device and Bootloader may be updated and sent together. In case of additional application file included,
	 * the service will try to send Soft Device, Bootloader and Application together (which is not supported currently) and if it fails, send first SD+BL, reconnect and send the application
	 * in the following connection.
	 * </p>
	 * <p>
	 * Since the DFU Library 0.5 you may specify the Init packet, that will be send prior to the firmware. The init packet contains some verification data, like a device type and
	 * revision, application version or a list of supported Soft Devices. The Init packet is required if Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * In case of using the compatibility ZIP files the Init packet for the Soft Device and Bootloader must be in the 'system.dat' file while for the application
	 * in the 'application.dat' file (included in the ZIP). The CRC in the 'system.dat' must be a CRC of both BIN contents if both a Soft Device and a Bootloader is present.
	 * </p>
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_AUTO = 0x00;
	/**
	 * An extra field with progress and error information used in broadcast events.
	 */
	public static final String EXTRA_DATA = "no.nordicsemi.android.dfu.extra.EXTRA_DATA";
	/**
	 * An extra field to send the progress or error information in the DFU notification. The value may contain:
	 * <ul>
	 * <li>Value 0 - 100 - percentage progress value</li>
	 * <li>One of the following status constants:
	 * <ul>
	 * <li>{@link #PROGRESS_CONNECTING}</li>
	 * <li>{@link #PROGRESS_STARTING}</li>
	 * <li>{@link #PROGRESS_ENABLING_DFU_MODE}</li>
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * <li>{@link #PROGRESS_DISCONNECTING}</li>
	 * <li>{@link #PROGRESS_COMPLETED}</li>
	 * <li>{@link #PROGRESS_ABORTED}</li>
	 * </ul>
	 * </li>
	 * <li>An error code with {@link #ERROR_MASK} if initialization error occurred</li>
	 * <li>An error code with {@link #ERROR_REMOTE_MASK} if remote DFU target returned an error</li>
	 * <li>An error code with {@link #ERROR_CONNECTION_MASK} if connection error occurred (f.e. GATT error (133) or Internal GATT Error (129))</li>
	 * </ul>
	 * To check if error occurred use:<br />
	 * {@code boolean error = progressValue >= DfuBaseService.ERROR_MASK;}
	 */
	public static final String EXTRA_PROGRESS = "no.nordicsemi.android.dfu.extra.EXTRA_PROGRESS";
	/**
	 * The number of currently transferred part. The SoftDevice and Bootloader may be send together as one part. If user wants to upload them together with an application it has to be sent
	 * in another connection as the second part.
	 *
	 * @see no.nordicsemi.android.dfu.DfuBaseService#EXTRA_PARTS_TOTAL
	 */
	public static final String EXTRA_PART_CURRENT = "no.nordicsemi.android.dfu.extra.EXTRA_PART_CURRENT";
	/**
	 * Number of parts in total.
	 *
	 * @see no.nordicsemi.android.dfu.DfuBaseService#EXTRA_PART_CURRENT
	 */
	public static final String EXTRA_PARTS_TOTAL = "no.nordicsemi.android.dfu.extra.EXTRA_PARTS_TOTAL";
	/**
	 * The current upload speed in bytes/millisecond.
	 */
	public static final String EXTRA_SPEED_B_PER_MS = "no.nordicsemi.android.dfu.extra.EXTRA_SPEED_B_PER_MS";
	/**
	 * The average upload speed in bytes/millisecond for the current part.
	 */
	public static final String EXTRA_AVG_SPEED_B_PER_MS = "no.nordicsemi.android.dfu.extra.EXTRA_AVG_SPEED_B_PER_MS";
	/**
	 * The broadcast message contains the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DATA} - the progress value (percentage 0-100) or:
	 * <ul>
	 * <li>{@link #PROGRESS_CONNECTING}</li>
	 * <li>{@link #PROGRESS_STARTING}</li>
	 * <li>{@link #PROGRESS_ENABLING_DFU_MODE}</li>
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * <li>{@link #PROGRESS_DISCONNECTING}</li>
	 * <li>{@link #PROGRESS_COMPLETED}</li>
	 * <li>{@link #PROGRESS_ABORTED}</li>
	 * </ul>
	 * </li>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - the target device address</li>
	 * <li>{@link #EXTRA_PART_CURRENT} - the number of currently transmitted part</li>
	 * <li>{@link #EXTRA_PARTS_TOTAL} - total number of parts that are being sent, f.e. if a ZIP file contains a Soft Device, a Bootloader and an Application,
	 * the SoftDevice and Bootloader will be send together as one part. Then the service will disconnect and reconnect to the new Bootloader and send the
	 * application as part number two.</li>
	 * <li>{@link #EXTRA_SPEED_B_PER_MS} - current speed in bytes/millisecond as float</li>
	 * <li>{@link #EXTRA_AVG_SPEED_B_PER_MS} - the average transmission speed in bytes/millisecond as float</li>
	 * </ul>
	 */
	public static final String BROADCAST_PROGRESS = "no.nordicsemi.android.dfu.broadcast.BROADCAST_PROGRESS";
	/**
	 * Service is connecting to the remote DFU target.
	 */
	public static final int PROGRESS_CONNECTING = -1;
	/**
	 * Service is enabling notifications and starting transmission.
	 */
	public static final int PROGRESS_STARTING = -2;
	/**
	 * Service has triggered a switch to bootloader mode. Now the service waits for the link loss event (this may take up to several seconds) and will connect again
	 * to the same device, now started in the bootloader mode.
	 */
	public static final int PROGRESS_ENABLING_DFU_MODE = -3;
	/**
	 * Service is sending validation request to the remote DFU target.
	 */
	public static final int PROGRESS_VALIDATING = -4;
	/**
	 * Service is disconnecting from the DFU target.
	 */
	public static final int PROGRESS_DISCONNECTING = -5;
	/**
	 * The connection is successful.
	 */
	public static final int PROGRESS_COMPLETED = -6;
	/**
	 * The upload has been aborted. Previous software version will be restored on the target.
	 */
	public static final int PROGRESS_ABORTED = -7;
	/**
	 * The broadcast error message contains the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DATA} - the error number. Use {@link GattError#parse(int)} to get String representation</li>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - the target device address</li>
	 * </ul>
	 */
	public static final String BROADCAST_ERROR = "no.nordicsemi.android.dfu.broadcast.BROADCAST_ERROR";
	/**
	 * The type of the error. This extra contains information about that kind of error has occurred. Connection state errors and other errors may share the same numbers.
	 * For example, the {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)} method may return a status code 8 (GATT INSUF AUTHORIZATION),
	 * while the status code 8 returned by {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)} is a GATT CONN TIMEOUT error.
	 */
	public static final String EXTRA_ERROR_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_ERROR_TYPE";
	public static final int ERROR_TYPE_OTHER = 0;
	public static final int ERROR_TYPE_COMMUNICATION_STATE = 1;
	public static final int ERROR_TYPE_COMMUNICATION = 2;
	public static final int ERROR_TYPE_DFU_REMOTE = 3;
	/**
	 * If this bit is set than the progress value indicates an error. Use {@link GattError#parse(int)} to obtain error name.
	 */
	public static final int ERROR_MASK = 0x1000;
	public static final int ERROR_DEVICE_DISCONNECTED = ERROR_MASK; // | 0x00;
	public static final int ERROR_FILE_NOT_FOUND = ERROR_MASK | 0x01;
	/**
	 * Thrown if service was unable to open the file ({@link java.io.IOException} has been thrown).
	 */
	public static final int ERROR_FILE_ERROR = ERROR_MASK | 0x02;
	/**
	 * Thrown when input file is not a valid HEX or ZIP file.
	 */
	public static final int ERROR_FILE_INVALID = ERROR_MASK | 0x03;
	/**
	 * Thrown when {@link java.io.IOException} occurred when reading from file.
	 */
	public static final int ERROR_FILE_IO_EXCEPTION = ERROR_MASK | 0x04;
	/**
	 * Error thrown when {@code gatt.discoverServices();} returns false.
	 */
	public static final int ERROR_SERVICE_DISCOVERY_NOT_STARTED = ERROR_MASK | 0x05;
	/**
	 * Thrown when the service discovery has finished but the DFU service has not been found. The device does not support DFU of is not in DFU mode.
	 */
	public static final int ERROR_SERVICE_NOT_FOUND = ERROR_MASK | 0x06;
	/**
	 * Thrown when the required DFU service has been found but at least one of the DFU characteristics is absent.
	 */
	public static final int ERROR_CHARACTERISTICS_NOT_FOUND = ERROR_MASK | 0x07;
	/**
	 * Thrown when unknown response has been obtained from the target. The DFU target must follow specification.
	 */
	public static final int ERROR_INVALID_RESPONSE = ERROR_MASK | 0x08;
	/**
	 * Thrown when the the service does not support given type or mime-type.
	 */
	public static final int ERROR_FILE_TYPE_UNSUPPORTED = ERROR_MASK | 0x09;
	/**
	 * Thrown when the the Bluetooth adapter is disabled.
	 */
	public static final int ERROR_BLUETOOTH_DISABLED = ERROR_MASK | 0x0A;
	/**
	 * DFU Bootloader version 0.6+ requires sending the Init packet. If such bootloader version is detected, but the init packet has not been set this error is thrown.
	 */
	public static final int ERROR_INIT_PACKET_REQUIRED = ERROR_MASK | 0x0B;
    /**
     * Thrown when the firmware file is not word-aligned. The firmware size must be dividable by 4 bytes.
     */
    public static final int ERROR_FILE_SIZE_INVALID = ERROR_MASK | 0x0C;
	/**
	 * Flag set when the DFU target returned a DFU error. Look for DFU specification to get error codes.
	 */
	public static final int ERROR_REMOTE_MASK = 0x2000;
	/**
	 * The flag set when one of {@link android.bluetooth.BluetoothGattCallback} methods was called with status other than {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS}.
	 */
	public static final int ERROR_CONNECTION_MASK = 0x4000;
	/**
	 * The flag set when the {@link android.bluetooth.BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)} method was called with
	 * status other than {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS}.
	 */
	public static final int ERROR_CONNECTION_STATE_MASK = 0x8000;
	/**
	 * The log events are only broadcast when there is no nRF Logger installed. The broadcast contains 2 extras:
	 * <ul>
	 * <li>{@link #EXTRA_LOG_LEVEL} - The log level, one of following: {@link #LOG_LEVEL_DEBUG}, {@link #LOG_LEVEL_VERBOSE}, {@link #LOG_LEVEL_INFO},
	 * {@link #LOG_LEVEL_APPLICATION}, {@link #LOG_LEVEL_WARNING}, {@link #LOG_LEVEL_ERROR}</li>
	 * <li>{@link #EXTRA_LOG_MESSAGE}</li> - The log message
	 * </ul>
	 */
	public static final String BROADCAST_LOG = "no.nordicsemi.android.dfu.broadcast.BROADCAST_LOG";
	public static final String EXTRA_LOG_MESSAGE = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_INFO";
	public static final String EXTRA_LOG_LEVEL = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_LEVEL";
	/*
	 * Note:
	 * The nRF Logger API library has been excluded from the DfuLibrary.
	 * All log events are now being sent using local broadcasts and may be logged into nRF Logger in the app module.
	 * This is to make the Dfu module independent from logging tool.
	 *
	 * The log levels below are equal to log levels in nRF Logger API library, v 2.0.
	 * @see https://github.com/NordicSemiconductor/nRF-Logger-API
	 */
	/**
	 * Level used just for debugging purposes. It has lowest level
	 */
	public final static int LOG_LEVEL_DEBUG = 0;
	/**
	 * Log entries with minor importance
	 */
	public final static int LOG_LEVEL_VERBOSE = 1;
	/**
	 * Default logging level for important entries
	 */
	public final static int LOG_LEVEL_INFO = 5;
	/**
	 * Log entries level for applications
	 */
	public final static int LOG_LEVEL_APPLICATION = 10;
	/**
	 * Log entries with high importance
	 */
	public final static int LOG_LEVEL_WARNING = 15;
	/**
	 * Log entries with very high importance, like errors
	 */
	public final static int LOG_LEVEL_ERROR = 20;
	/**
	 * Activity may broadcast this broadcast in order to pause, resume or abort DFU process.
	 * Use {@link #EXTRA_ACTION} extra to pass the action.
	 */
	public static final String BROADCAST_ACTION = "no.nordicsemi.android.dfu.broadcast.BROADCAST_ACTION";
	/**
	 * The action extra. It may have one of the following values: {@link #ACTION_PAUSE}, {@link #ACTION_RESUME}, {@link #ACTION_ABORT}.
	 */
	public static final String EXTRA_ACTION = "no.nordicsemi.android.dfu.extra.EXTRA_ACTION";
	/** Pauses the upload. The service will wait for broadcasts with the action set to {@link #ACTION_RESUME} or {@link #ACTION_ABORT}. */
	public static final int ACTION_PAUSE = 0;
	/** Resumes the upload that has been paused before using {@link #ACTION_PAUSE}. */
	public static final int ACTION_RESUME = 1;
	/**
	 * Aborts the upload. The service does not need to be paused before.
	 * After sending {@link #BROADCAST_ACTION} with extra {@link #EXTRA_ACTION} set to this value the DFU bootloader will restore the old application
	 * (if there was already an application). Be aware that uploading the Soft Device will erase the application in order to make space in the memory.
	 * In case there is no application, or the application has been removed, the DFU bootloader will be started and user may try to send the application again.
	 * The bootloader may advertise with the address incremented by 1 to prevent caching services.
	 */
	public static final int ACTION_ABORT = 2;

	/**
	 * Lock used in synchronization purposes
	 */
	private final Object mLock = new Object();
	private BluetoothAdapter mBluetoothAdapter;
	private String mDeviceAddress;
	private String mDeviceName;
	private boolean mDisableNotification;
	/**
	 * Stores the last progress percent. Used to prevent from sending progress notifications with the same value.
	 * @see #updateProgressNotification(int)
	 */
	private int mLastProgress = -1;
	/**
	 * This value is used to calculate the current transfer speed.
	 */
	private int mLastBytesSent;
	private long mLastNotificationTime, mLastProgressTime, mStartTime;

	private BaseDfuImpl mDfuImpl;

	private final BroadcastReceiver mDfuActionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int action = intent.getIntExtra(EXTRA_ACTION, 0);

			logi("User action received: " + action);
			switch (action) {
				case ACTION_PAUSE:
					mDfuImpl.pause();
					break;
				case ACTION_RESUME:
					mDfuImpl.resume();
					break;
				case ACTION_ABORT:
					mDfuImpl.abort();
					break;
			}
		}
	};

	public DfuBaseService() {
		super(TAG);
	}

	private static IntentFilter makeDfuActionIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(DfuBaseService.BROADCAST_ACTION);
		return intentFilter;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		initialize();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		final IntentFilter actionFilter = makeDfuActionIntentFilter();
		manager.registerReceiver(mDfuActionReceiver, actionFilter);
		registerReceiver(mDfuActionReceiver, actionFilter); // Additionally we must register this receiver as a non-local to get broadcasts from the notification actions
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.unregisterReceiver(mDfuActionReceiver);

		unregisterReceiver(mDfuActionReceiver);
		if (mDfuImpl != null) {
			mDfuImpl.unregister();
		}
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		// Read input parameters
		final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
		final String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
		final boolean disableNotification = intent.getBooleanExtra(EXTRA_DISABLE_NOTIFICATION, false);
		final String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
		final Uri fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
		final int fileResId = intent.getIntExtra(EXTRA_FILE_RES_ID, 0);
		final String initFilePath = intent.getStringExtra(EXTRA_INIT_FILE_PATH);
		final Uri initFileUri = intent.getParcelableExtra(EXTRA_INIT_FILE_URI);
		final int initFileResId = intent.getIntExtra(EXTRA_INIT_FILE_RES_ID, 0);
		int fileType = intent.getIntExtra(EXTRA_FILE_TYPE, TYPE_AUTO);
		if (filePath != null && fileType == TYPE_AUTO)
			fileType = filePath.toLowerCase(Locale.US).endsWith("zip") ? TYPE_AUTO : TYPE_APPLICATION;
		String mimeType = intent.getStringExtra(EXTRA_FILE_MIME_TYPE);
		mimeType = mimeType != null ? mimeType : (fileType == TYPE_AUTO ? MIME_TYPE_ZIP : MIME_TYPE_OCTET_STREAM);
		mPartCurrent = intent.getIntExtra(EXTRA_PART_CURRENT, 1);
		mPartsTotal = intent.getIntExtra(EXTRA_PARTS_TOTAL, 1);

		// Check file type and mime-type
		if ((fileType & ~(TYPE_SOFT_DEVICE | TYPE_BOOTLOADER | TYPE_APPLICATION)) > 0 || !(MIME_TYPE_ZIP.equals(mimeType) || MIME_TYPE_OCTET_STREAM.equals(mimeType))) {
			logw("File type or file mime-type not supported");
			sendLogBroadcast(LOG_LEVEL_WARNING, "File type or file mime-type not supported");
			sendErrorBroadcast(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}
		if (MIME_TYPE_OCTET_STREAM.equals(mimeType) && fileType != TYPE_SOFT_DEVICE && fileType != TYPE_BOOTLOADER && fileType != TYPE_APPLICATION) {
			logw("Unable to determine file type");
			sendLogBroadcast(LOG_LEVEL_WARNING, "Unable to determine file type");
			sendErrorBroadcast(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}

		mDeviceAddress = deviceAddress;
		mDeviceName = deviceName;
		mDisableNotification = disableNotification;
		mConnectionState = STATE_DISCONNECTED;
		mBytesSent = 0;
		mBytesConfirmed = 0;
		mPacketsSentSinceNotification = 0;
		mError = 0;
		mLastProgressTime = 0;
		mAborted = false;
		mPaused = false;
		mNotificationsEnabled = false;
		mResetRequestSent = false;
		mRequestCompleted = false;
		mImageSizeSent = false;
		mRemoteErrorOccurred = false;

		// Read preferences
		final boolean packetReceiptNotificationEnabled = preferences.getBoolean(DfuSettingsConstants.SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED, true);
		String value = preferences.getString(DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS, String.valueOf(DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS_DEFAULT));
		int numberOfPackets;
		try {
			numberOfPackets = Integer.parseInt(value);
			if (numberOfPackets < 0 || numberOfPackets > 0xFFFF)
				numberOfPackets = DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS_DEFAULT;
		} catch (final NumberFormatException e) {
			numberOfPackets = DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS_DEFAULT;
		}
		if (!packetReceiptNotificationEnabled)
			numberOfPackets = 0;
		mPacketsBeforeNotification = numberOfPackets;
		// The Soft Device starts where MBR ends (by default from the address 0x1000). Before there is a MBR section, which should not be transmitted over DFU.
		// Applications and bootloader starts from bigger address. However, in custom DFU implementations, user may want to transmit the whole whole data, even from address 0x0000.
		value = preferences.getString(DfuSettingsConstants.SETTINGS_MBR_SIZE, String.valueOf(DfuSettingsConstants.SETTINGS_DEFAULT_MBR_SIZE));
		int mbrSize;
		try {
			mbrSize = Integer.parseInt(value);
			if (mbrSize < 0)
				mbrSize = 0;
		} catch (final NumberFormatException e) {
			mbrSize = DfuSettingsConstants.SETTINGS_DEFAULT_MBR_SIZE;
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
		final boolean assumeDfuMode = preferences.getBoolean(DfuSettingsConstants.SETTINGS_ASSUME_DFU_NODE, false);

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "DFU service started");

		/*
		 * First the service is trying to read the firmware and init packet files.
		 */
		InputStream is = null;
		InputStream initIs = null;
		int imageSizeInBytes;
		try {
			// Prepare data to send, calculate stream size
			try {
				sendLogBroadcast(LOG_LEVEL_VERBOSE, "Opening file...");
				if (fileUri != null) {
					is = openInputStream(fileUri, mimeType, mbrSize, fileType);
				} else if (filePath != null) {
					is = openInputStream(filePath, mimeType, mbrSize, fileType);
				} else if (fileResId > 0) {
					is = openInputStream(fileResId, mimeType, mbrSize, fileType);
				}

				if (initFileUri != null) {
					// Try to read the Init Packet file from URI
					initIs = getContentResolver().openInputStream(initFileUri);
				} else if (initFilePath != null) {
					// Try to read the Init Packet file from path
					initIs = new FileInputStream(initFilePath);
				} else if (initFileResId > 0) {
					// Try to read the Init Packet file from given resource
					initIs = getResources().openRawResource(initFileResId);
				}

				mInputStream = is;
				imageSizeInBytes = mImageSizeInBytes = is.available();

                if ((imageSizeInBytes % 4) != 0)
                    throw new SizeValidationException("The new firmware is not word-aligned.");

				// Update the file type bit field basing on the ZIP content
				if (fileType == TYPE_AUTO && MIME_TYPE_ZIP.equals(mimeType)) {
					final ArchiveInputStream zhis = (ArchiveInputStream) is;
					fileType = zhis.getContentType();
				}
				mFileType = fileType;
				// Set the Init packet stream in case of a ZIP file
				if (MIME_TYPE_ZIP.equals(mimeType)) {
					final ArchiveInputStream zhis = (ArchiveInputStream) is;

                    // Validate sizes
                    if ((fileType & TYPE_APPLICATION) > 0 && (zhis.applicationImageSize() % 4) != 0)
                        throw new SizeValidationException("Application firmware is not word-aligned.");
                    if ((fileType & TYPE_BOOTLOADER) > 0 && (zhis.bootloaderImageSize() % 4) != 0)
                        throw new SizeValidationException("Bootloader firmware is not word-aligned.");
                    if ((fileType & TYPE_SOFT_DEVICE) > 0 && (zhis.softDeviceImageSize() % 4) != 0)
                        throw new SizeValidationException("Soft Device firmware is not word-aligned.");

					if (fileType == TYPE_APPLICATION) {
						if (zhis.getApplicationInit() != null)
							initIs = new ByteArrayInputStream(zhis.getApplicationInit());
					} else {
						if (zhis.getSystemInit() != null)
							initIs = new ByteArrayInputStream(zhis.getSystemInit());
					}
				}
				sendLogBroadcast(LOG_LEVEL_INFO, "Image file opened (" + mImageSizeInBytes + " bytes in total)");
			} catch (final SecurityException e) {
				loge("A security exception occurred while opening file", e);
				updateProgressNotification(ERROR_FILE_NOT_FOUND);
				return;
			} catch (final FileNotFoundException e) {
				loge("An exception occurred while opening file", e);
				updateProgressNotification(ERROR_FILE_NOT_FOUND);
				return;
            } catch (final SizeValidationException e) {
                loge("Firmware not word-aligned", e);
                updateProgressNotification(ERROR_FILE_SIZE_INVALID);
                return;
			} catch (final IOException e) {
				loge("An exception occurred while calculating file size", e);
				updateProgressNotification(ERROR_FILE_ERROR);
				return;
			} catch (final Exception e) {
				loge("An exception occurred while opening files. Did you set the firmware file?", e);
				updateProgressNotification(ERROR_FILE_ERROR);
				return;
			}

			// Wait a second... If we were connected before it's good to give some time before we start reconnecting.
			synchronized (this) {
				try {
					sendLogBroadcast(LOG_LEVEL_DEBUG, "wait(1000)");
					wait(1000);
				} catch (final InterruptedException e) {
					// do nothing
				}
			}

			/*
			 * Now let's connect to the device.
			 * All the methods below are synchronous. The mLock object is used to wait for asynchronous calls.
			 */
			sendLogBroadcast(LOG_LEVEL_VERBOSE, "Connecting to DFU target...");
			updateProgressNotification(PROGRESS_CONNECTING);

			final BluetoothGatt gatt = connect(deviceAddress);
			// Are we connected?
			if (gatt == null) {
				loge("Bluetooth adapter disabled");
				sendLogBroadcast(LOG_LEVEL_ERROR, "Bluetooth adapter disabled");
				updateProgressNotification(ERROR_BLUETOOTH_DISABLED);
				return;
			}
			if (mError > 0) { // error occurred
				final int error = mError & ~ERROR_CONNECTION_STATE_MASK;
				loge("An error occurred while connecting to the device:" + error);
				sendLogBroadcast(LOG_LEVEL_ERROR, String.format("Connection failed (0x%02X): %s", error, GattError.parseConnectionError(error)));
				terminateConnection(gatt, mError);
				return;
			}
			if (mAborted) {
				logi("Upload aborted");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
				terminateConnection(gatt, PROGRESS_ABORTED);
				return;
			}

			// We have connected to DFU device and services are discoverer
			final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID); // there was a case when the service was null. I don't know why
			if (dfuService == null) {
				loge("DFU service does not exists on the device");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Connected. DFU Service not found");
				terminateConnection(gatt, ERROR_SERVICE_NOT_FOUND);
				return;
			}
			final BluetoothGattCharacteristic controlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
			final BluetoothGattCharacteristic packetCharacteristic = dfuService.getCharacteristic(DFU_PACKET_UUID);
			if (controlPointCharacteristic == null || packetCharacteristic == null) {
				loge("DFU characteristics not found in the DFU service");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Connected. DFU Characteristics not found");
				terminateConnection(gatt, ERROR_CHARACTERISTICS_NOT_FOUND);
				return;
			}
			/*
			 * The DFU Version characteristic has been added in SDK 7.0.
			 *
			 * It may return version number in 2 bytes (f.e. 0x05-00), where the first one is the minor version and the second one is the major version.
			 * In case of 0x05-00 the DFU has the version 0.5.
			 *
			 * Currently the following version numbers are supported:
			 *
			 *   - 0.1 (0x01-00) - The service is connected to the device in application mode, not to the DFU Bootloader. The application supports Long Term Key (LTK)
			 *                     sharing and buttonless update. Enable notifications on the DFU Control Point characteristic and write 0x01-04 into it to jump to the Bootloader.
			 *                     Check the Bootloader version again for more info about the Bootloader version.
			 *
			 *   - 0.5 (0x05-00) - The device is in the OTA-DFU Bootloader mode. The Bootloader supports LTK sharing and requires the Extended Init Packet. It supports
			 *                     a SoftDevice, Bootloader or an Application update. SoftDevice and a Bootloader may be sent together.
			 *
			 *   - 0.6 (0x06-00) - The device is in the OTA-DFU Bootloader mode. The DFU Bootloader is from SDK 8.0 and has the same features as version 0.5. It also
			 *                     supports also sending Service Changed notification in application mode after successful or aborted upload so no refreshing services is required.
			 */
			final BluetoothGattCharacteristic versionCharacteristic = dfuService.getCharacteristic(DFU_VERSION); // this may be null for older versions of the Bootloader

			sendLogBroadcast(LOG_LEVEL_INFO, "Services discovered");

			// Add one second delay to avoid the traffic jam before the DFU mode is enabled
			// Related:
			//   issue:        https://github.com/NordicSemiconductor/Android-DFU-Library/issues/10
			//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/12
			synchronized (this) {
				try {
					sendLogBroadcast(LOG_LEVEL_DEBUG, "wait(1000)");
					wait(1000);
				} catch (final InterruptedException e) {
					// Do nothing
				}
			}
			// End

			try {
				updateProgressNotification(PROGRESS_STARTING);

				/*
				 * Read the version number if available.
				 * The version number consists of 2 bytes: major and minor. Therefore f.e. the version 5 (00-05) can be read as 0.5.
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
				 *  								  Extended Init Packet. A change in the protocol will be required to fix this issue.
				 */
				int version = 0;
				if (versionCharacteristic != null) {
					version = readVersion(gatt, versionCharacteristic);
					final int minor = (version & 0x0F);
					final int major = (version >> 8);
					logi("Version number read: " + major + "." + minor);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Version number read: " + major + "." + minor);
				} else {
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "DFU Version characteristic not found");
				}

				/*
				 *  Check if we are in the DFU Bootloader or in the Application that supports the buttonless update.
				 *
				 *  In the DFU from SDK 6.1, which was also supporting the buttonless update, there was no DFU Version characteristic. In that case we may find out whether
				 *  we are in the bootloader or application by simply checking the number of characteristics. This may be overridden by setting the DfuSettingsConstants.SETTINGS_ASSUME_DFU_NODE
				 *  property to true in Shared Preferences.
				 */
				if (version == 1 || (!assumeDfuMode && version == 0 && gatt.getServices().size() > 3 /* No DFU Version char but more services than Generic Access, Generic Attribute, DFU Service */)) {
					// The service is connected to the application, not to the bootloader
					logw("Application with buttonless update found");
					sendLogBroadcast(LOG_LEVEL_WARNING, "Application with buttonless update found");

					// If we are bonded we may want to enable Service Changed characteristic indications.
					// Note: This feature will be introduced in the SDK 8.0 as this is the proper way to refresh attribute list on the phone.
					boolean hasServiceChanged = false;
					if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
						final BluetoothGattService genericAttributeService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE_UUID);
						if (genericAttributeService != null) {
							final BluetoothGattCharacteristic serviceChangedCharacteristic = genericAttributeService.getCharacteristic(SERVICE_CHANGED_UUID);
							if (serviceChangedCharacteristic != null) {
								// Let's read the current value of the Service Changed CCCD
								final boolean serviceChangedIndicationsEnabled = isServiceChangedCCCDEnabled(gatt, serviceChangedCharacteristic);

								if (!serviceChangedIndicationsEnabled) {
									enableCCCD(gatt, serviceChangedCharacteristic, INDICATIONS);
									sendLogBroadcast(LOG_LEVEL_APPLICATION, "Service Changed indications enabled");

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
									 *         See: \Nordic\nrf51\components\libraries\bootloader_dfu\dfu_types.h, line 56:
									 *          #define DFU_APP_DATA_RESERVED           0x0000  ->  0x0800+   //< Size of Application Data that must be preserved between application updates...
									 *     - Bootloader:
									 *         The Application memory should not be removed when the Bootloader is upgraded, so the Bootloader configuration does not matter.
									 *
									 * If the bond information is not to be preserved between the old and new applications, we may skip this disconnect/reconnect process.
									 * The DFU Bootloader will send the SD indication anyway when we will just continue here, as the information whether it should send it or not it is not being
									 * read from the application's ATT table, but rather passed as an argument of the "reboot to bootloader" method.
									 */
									final boolean keepBond = intent.getBooleanExtra(EXTRA_KEEP_BOND, false);
									if (keepBond && (fileType & TYPE_SOFT_DEVICE) == 0) {
										sendLogBroadcast(LOG_LEVEL_VERBOSE, "Restarting service...");

										// Disconnect
										disconnect(gatt);

										// Close the device
										close(gatt);

										logi("Restarting service");
										sendLogBroadcast(LOG_LEVEL_VERBOSE, "Restarting service...");
										final Intent newIntent = new Intent();
										newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
										startService(newIntent);
										return;
									}
								} else {
									sendLogBroadcast(LOG_LEVEL_APPLICATION, "Service Changed indications enabled");
								}
								hasServiceChanged = true;
							}
						}
					}

					sendLogBroadcast(LOG_LEVEL_VERBOSE, "Jumping to the DFU Bootloader...");

					// Enable notifications
					enableCCCD(gatt, controlPointCharacteristic, NOTIFICATIONS);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Notifications enabled");

					// Wait a second here before going further
					// Related:
					//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
					synchronized (this) {
						try {
							sendLogBroadcast(LOG_LEVEL_DEBUG, "wait(1000)");
							wait(1000);
						} catch (final InterruptedException e) {
							// Do nothing
						}
					}
					// End

					// Send 'jump to bootloader command' (Start DFU)
					updateProgressNotification(PROGRESS_ENABLING_DFU_MODE);
					OP_CODE_START_DFU[1] = 0x04;
					logi("Sending Start DFU command (Op Code = 1, Upload Mode = 4)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU, true);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Jump to bootloader sent (Op Code = 1, Upload Mode = 4)");

					// The device will reset so we don't have to send Disconnect signal.
					waitUntilDisconnected();
					sendLogBroadcast(LOG_LEVEL_INFO, "Disconnected by the remote device");

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
					 * However, as up to Android 5 the system does NOT respect this requirement and servers are cached for every device, even if Service Changed is enabled -> Android BUG?
					 * For bonded devices Android performs service re-discovery when SC indication is received.
					 */
					refreshDeviceCache(gatt, !hasServiceChanged);

					// Close the device
					close(gatt);

					logi("Starting service that will connect to the DFU bootloader");
					final Intent newIntent = new Intent();
					newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
					startService(newIntent);
					return;
				}

				/*
				 * If the DFU Version characteristic is present and the version returned from it is greater or equal to 0.5, the Extended Init Packet is required.
				 * If the InputStream with init packet is null we may safely abort sending and reset the device as it would happen eventually in few moments.
				 * The DFU target would send DFU INVALID STATE error if the init packet would not be sent before starting file transmission.
				 */
				if (version >= 5 && initIs == null) {
					logw("Init packet not set for the DFU Bootloader version " + version);
					sendLogBroadcast(LOG_LEVEL_ERROR, "The Init packet is required by this version DFU Bootloader");
					terminateConnection(gatt, ERROR_INIT_PACKET_REQUIRED);
					return;
				}

				// Enable notifications
				enableCCCD(gatt, controlPointCharacteristic, NOTIFICATIONS);
				sendLogBroadcast(LOG_LEVEL_APPLICATION, "Notifications enabled");

				// Wait a second here before going further
				// Related:
				//   pull request: https://github.com/NordicSemiconductor/Android-DFU-Library/pull/11
				synchronized (this) {
					try {
						sendLogBroadcast(LOG_LEVEL_DEBUG, "wait(1000)");
						wait(1000);
					} catch (final InterruptedException e) {
						// Do nothing
					}
				}
				// End

				try {
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
					int softDeviceImageSize = (fileType & TYPE_SOFT_DEVICE) > 0 ? imageSizeInBytes : 0;
					int bootloaderImageSize = (fileType & TYPE_BOOTLOADER) > 0 ? imageSizeInBytes : 0;
					int appImageSize = (fileType & TYPE_APPLICATION) > 0 ? imageSizeInBytes : 0;
					// The sizes above may be overwritten if a ZIP file was passed
					if (MIME_TYPE_ZIP.equals(mimeType)) {
						final ArchiveInputStream zhis = (ArchiveInputStream) is;
						softDeviceImageSize = zhis.softDeviceImageSize();
						bootloaderImageSize = zhis.bootloaderImageSize();
						appImageSize = zhis.applicationImageSize();
					}

					try {
						OP_CODE_START_DFU[1] = (byte) fileType;

						// Send Start DFU command to Control Point
						logi("Sending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1, Upload Mode = " + fileType + ")");

						// Send image size in bytes to DFU Packet
						logi("Sending image size array to DFU Packet (" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b)");
						writeImageSize(gatt, packetCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Firmware image size sent (" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b)");

						// A notification will come with confirmation. Let's wait for it a bit
						response = readNotificationResponse();

						/*
						 * The response received from the DFU device contains:
						 * +---------+--------+----------------------------------------------------+
						 * | byte no | value  | description                                        |
						 * +---------+--------+----------------------------------------------------+
						 * | 0       | 16     | Response code                                      |
						 * | 1       | 1      | The Op Code of a request that this response is for |
						 * | 2       | STATUS | See DFU_STATUS_* for status codes                  |
						 * +---------+--------+----------------------------------------------------+
						 */
						status = getStatusCode(response, OP_CODE_START_DFU_KEY);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + " Status = " + status + ")");
						if (status != DFU_STATUS_SUCCESS)
							throw new RemoteDfuException("Starting DFU failed", status);
					} catch (final RemoteDfuException e) {
						try {
							if (e.getErrorNumber() != DFU_STATUS_NOT_SUPPORTED)
								throw e;

							// If user wants to send the Soft Device and/or the Bootloader + Application we may try to send the Soft Device/Bootloader files first,
							// and then reconnect and send the application in the second connection.
							if ((fileType & TYPE_APPLICATION) > 0 && (fileType & (TYPE_SOFT_DEVICE | TYPE_BOOTLOADER)) > 0) {
								// Clear the remote error flag
								mRemoteErrorOccurred = false;

								logw("DFU target does not support (SD/BL)+App update");
								sendLogBroadcast(LOG_LEVEL_WARNING, "DFU target does not support (SD/BL)+App update");

								fileType &= ~TYPE_APPLICATION; // clear application bit
								mFileType = fileType;
								OP_CODE_START_DFU[1] = (byte) fileType;
								mPartsTotal = 2;

								// Set new content type in the ZIP Input Stream and update sizes of images
								final ArchiveInputStream zhis = (ArchiveInputStream) is;
								zhis.setContentType(fileType);
								try {
									appImageSize = 0;
									mImageSizeInBytes = is.available();
								} catch (final IOException e1) {
									// never happen
								}

								// Send Start DFU command to Control Point
								sendLogBroadcast(LOG_LEVEL_VERBOSE, "Sending only SD/BL");
								logi("Resending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
								writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU);
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1, Upload Mode = " + fileType + ")");

								// Send image size in bytes to DFU Packet
								logi("Sending image size array to DFU Packet: [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");
								writeImageSize(gatt, packetCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "Firmware image size sent [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");

								// A notification will come with confirmation. Let's wait for it a bit
								response = readNotificationResponse();
								status = getStatusCode(response, OP_CODE_START_DFU_KEY);
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + " Status = " + status + ")");
								if (status != DFU_STATUS_SUCCESS)
									throw new RemoteDfuException("Starting DFU failed", status);
							} else
								throw e;
						} catch (final RemoteDfuException e1) {
							if (e1.getErrorNumber() != DFU_STATUS_NOT_SUPPORTED)
								throw e1;

							// If operation is not supported by DFU target we may try to upload application with legacy mode, using the old DFU protocol
							if (fileType == TYPE_APPLICATION) {
								// Clear the remote error flag
								mRemoteErrorOccurred = false;

								// The DFU target does not support DFU v.2 protocol
								logw("DFU target does not support DFU v.2");
								sendLogBroadcast(LOG_LEVEL_WARNING, "DFU target does not support DFU v.2");

								// Send Start DFU command to Control Point
								sendLogBroadcast(LOG_LEVEL_VERBOSE, "Switching to DFU v.1");
								logi("Resending Start DFU command (Op Code = 1)");
								writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU); // If has 2 bytes, but the second one is ignored
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "DFU Start sent (Op Code = 1)");

								// Send image size in bytes to DFU Packet
								logi("Sending application image size to DFU Packet: " + imageSizeInBytes + " bytes");
								writeImageSize(gatt, packetCharacteristic, mImageSizeInBytes);
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "Firmware image size sent (" + imageSizeInBytes + " bytes)");

								// A notification will come with confirmation. Let's wait for it a bit
								response = readNotificationResponse();
								status = getStatusCode(response, OP_CODE_START_DFU_KEY);
								sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
								if (status != DFU_STATUS_SUCCESS)
									throw new RemoteDfuException("Starting DFU failed", status);
							} else
								throw e1;
						}
					}

					// Since SDK 6.1 this delay is no longer required as the Receive Start DFU notification is postponed until the memory is clear.

					//		if ((fileType & TYPE_SOFT_DEVICE) > 0) {
					//			// In the experimental version of bootloader (SDK 6.0.0) we must wait some time until we can proceed with Soft Device update. Bootloader must prepare the RAM for the new firmware.
					//			// Most likely this step will not be needed in the future as the notification received a moment before will be postponed until Bootloader is ready.
					//			synchronized (this) {
					//				try {
					//					wait(6000);
					//				} catch (final InterruptedException e) {
					//					// do nothing
					//				}
					//			}
					//		}

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
					if (initIs != null) {
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Writing Initialize DFU Parameters...");

						logi("Sending the Initialize DFU Parameters START (Op Code = 2, Value = 0)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_INIT_DFU_PARAMS_START);

						try {
							byte[] data = new byte[20];
							int size;
							while ((size = initIs.read(data, 0, data.length)) != -1) {
								writeInitPacket(gatt, packetCharacteristic, data, size);
							}
						} catch (final IOException e) {
							loge("Error while reading Init packet file");
							throw new DfuException("Error while reading Init packet file", ERROR_FILE_ERROR);
						}
						logi("Sending the Initialize DFU Parameters COMPLETE (Op Code = 2, Value = 1)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_INIT_DFU_PARAMS_COMPLETE);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Initialize DFU Parameters completed");

						// A notification will come with confirmation. Let's wait for it a bit
						response = readNotificationResponse();
						status = getStatusCode(response, OP_CODE_INIT_DFU_PARAMS_KEY);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
						if (status != DFU_STATUS_SUCCESS)
							throw new RemoteDfuException("Device returned error after sending init packet", status);
					} else
						mInitPacketSent = true;

					// Send the number of packets of firmware before receiving a receipt notification
					final int numberOfPacketsBeforeNotification = mPacketsBeforeNotification;
					if (numberOfPacketsBeforeNotification > 0) {
						logi("Sending the number of packets before notifications (Op Code = 8, Value = " + numberOfPacketsBeforeNotification + ")");
						setNumberOfPackets(OP_CODE_PACKET_RECEIPT_NOTIF_REQ, numberOfPacketsBeforeNotification);
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_PACKET_RECEIPT_NOTIF_REQ);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Packet Receipt Notif Req (Op Code = 8) sent (Value = " + numberOfPacketsBeforeNotification + ")");
					}

					// Initialize firmware upload
					logi("Sending Receive Firmware Image request (Op Code = 3)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RECEIVE_FIRMWARE_IMAGE);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Receive Firmware Image request sent");

					// Send the firmware. The method below sends the first packet and waits until the whole firmware is sent.
					final long startTime = mLastProgressTime = mStartTime = SystemClock.elapsedRealtime();
					updateProgressNotification();
					try {
						logi("Uploading firmware...");
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Uploading firmware...");
						response = uploadFirmwareImage(gatt, packetCharacteristic, is);
					} catch (final DeviceDisconnectedException e) {
						loge("Disconnected while sending data");
						throw e;
						// TODO reconnect?
					}
					final long endTime = SystemClock.elapsedRealtime();

					// Check the result of the operation
					status = getStatusCode(response, OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY);
					logi("Response received. Op Code: " + response[0] + " Req Op Code = " + response[1] + ", Status = " + response[2]);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
					if (status != DFU_STATUS_SUCCESS)
						throw new RemoteDfuException("Device returned error after sending file", status);

					logi("Transfer of " + mBytesSent + " bytes has taken " + (endTime - startTime) + " ms");
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Upload completed in " + (endTime - startTime) + " ms");

					// Send Validate request
					logi("Sending Validate request (Op Code = 4)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_VALIDATE);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Validate request sent");

					// A notification will come with status code. Let's wait for it a bit.
					response = readNotificationResponse();
					status = getStatusCode(response, OP_CODE_VALIDATE_KEY);
					logi("Response received. Op Code: " + response[0] + " Req Op Code = " + response[1] + ", Status = " + response[2]);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Response received (Op Code = " + response[1] + ", Status = " + status + ")");
					if (status != DFU_STATUS_SUCCESS)
						throw new RemoteDfuException("Device returned validation error", status);

					// Send Activate and Reset signal.
					updateProgressNotification(PROGRESS_DISCONNECTING);
					logi("Sending Activate and Reset request (Op Code = 5)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_ACTIVATE_AND_RESET);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Activate and Reset request sent");

					// The device will reset so we don't have to send Disconnect signal.
					waitUntilDisconnected();
					sendLogBroadcast(LOG_LEVEL_INFO, "Disconnected by the remote device");

					// In the DFU version 0.5, in case the device is bonded, the target device does not send the Service Changed indication after
					// a jump from bootloader mode to app mode. This issue has been fixed in DFU version 0.6 (SDK 8.0). If the DFU bootloader has been
					// configured to preserve the bond information we do not need to enforce refreshing services, as it will notify the phone using the
					// Service Changed indication.
					final boolean keepBond = intent.getBooleanExtra(EXTRA_KEEP_BOND, false);
					refreshDeviceCache(gatt, version == 5 || !keepBond);

					// Close the device
					close(gatt);

					// During the update the bonding information on the target device may have been removed.
					// To create bond with the new application set the EXTRA_RESTORE_BOND extra to true.
					// In case the bond information is copied to the new application the new bonding is not required.
					if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
						final boolean restoreBond = intent.getBooleanExtra(EXTRA_RESTORE_BOND, false);

						if (restoreBond || !keepBond || (fileType & TYPE_SOFT_DEVICE) > 0) {
							// The bond information was lost.
							removeBond(gatt.getDevice());

							// Give some time for removing the bond information. 300ms was to short, let's set it to 2 seconds just to be sure.
							synchronized (this) {
								try {
									wait(2000);
								} catch (InterruptedException e) {
									// do nothing
								}
							}
						}

						if (restoreBond && (fileType & TYPE_APPLICATION) > 0) {
							// Restore pairing when application was updated.
							createBond(gatt.getDevice());
						}
					}

					/*
					 * We need to send PROGRESS_COMPLETED message only when all files has been transmitted.
					 * In case you want to send the Soft Device and/or Bootloader and the Application, the service will be started twice: one to send SD+BL, and the
					 * second time to send the Application only (using the new Bootloader). In the first case we do not send PROGRESS_COMPLETED notification.
					 */
					if (mPartCurrent == mPartsTotal) {
						// Delay this event a little bit. Android needs some time to prepare for reconnection.
						synchronized (mLock) {
							try {
								mLock.wait(1400);
							} catch (final InterruptedException e) {
								// do nothing
							}
						}
						updateProgressNotification(PROGRESS_COMPLETED);
					} else {
						/*
						 * In case when the Soft Device has been upgraded, and the application should be send in the following connection, we have to
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
						sendLogBroadcast(LOG_LEVEL_VERBOSE, "Scanning for the DFU Bootloader...");
						final String newAddress = BootloaderScannerFactory.getScanner().searchFor(mDeviceAddress);
						if (newAddress != null)
							sendLogBroadcast(LOG_LEVEL_INFO, "DFU Bootloader found with address " + newAddress);
						else {
							sendLogBroadcast(LOG_LEVEL_INFO, "DFU Bootloader not found. Trying the same address...");
						}

						/*
						 * The current service instance has uploaded the Soft Device and/or Bootloader.
						 * We need to start another instance that will try to send application only.
						 */
						logi("Starting service that will upload application");
						final Intent newIntent = new Intent();
						newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
						newIntent.putExtra(EXTRA_FILE_MIME_TYPE, MIME_TYPE_ZIP); // ensure this is set (e.g. for scripts)
						newIntent.putExtra(EXTRA_FILE_TYPE, TYPE_APPLICATION); // set the type to application only
						if (newAddress != null)
							newIntent.putExtra(EXTRA_DEVICE_ADDRESS, newAddress);
						newIntent.putExtra(EXTRA_PART_CURRENT, mPartCurrent + 1);
						newIntent.putExtra(EXTRA_PARTS_TOTAL, mPartsTotal);
						startService(newIntent);
					}
				} catch (final UnknownResponseException e) {
					final int error = ERROR_INVALID_RESPONSE;
					loge(e.getMessage());
					sendLogBroadcast(LOG_LEVEL_ERROR, e.getMessage());

					logi("Sending Reset command (Op Code = 6)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Reset request sent");
					terminateConnection(gatt, error);
				} catch (final RemoteDfuException e) {
					final int error = ERROR_REMOTE_MASK | e.getErrorNumber();
					loge(e.getMessage());
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format("Remote DFU error: %s", GattError.parse(error)));

					logi("Sending Reset command (Op Code = 6)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
					sendLogBroadcast(LOG_LEVEL_APPLICATION, "Reset request sent");
					terminateConnection(gatt, error);
				}
			} catch (final UploadAbortedException e) {
				logi("Upload aborted");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
				if (mConnectionState == STATE_CONNECTED_AND_READY)
					try {
						mAborted = false;
						logi("Sending Reset command (Op Code = 6)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Reset request sent");
					} catch (final Exception e1) {
						// do nothing
					}
				terminateConnection(gatt, PROGRESS_ABORTED);
			} catch (final DeviceDisconnectedException e) {
				sendLogBroadcast(LOG_LEVEL_ERROR, "Device has disconnected");
				// TODO reconnect n times?
				loge(e.getMessage());
				close(gatt);
				updateProgressNotification(ERROR_DEVICE_DISCONNECTED);
			} catch (final DfuException e) {
				int error = e.getErrorNumber();
				// Connection state errors and other Bluetooth GATT callbacks share the same error numbers. Therefore we are using bit masks to identify the type.
				if ((error & ERROR_CONNECTION_STATE_MASK) > 0) {
					error &= ~ERROR_CONNECTION_STATE_MASK;
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format("Error (0x%02X): %s", error, GattError.parseConnectionError(error)));
				} else {
					error &= ~ERROR_CONNECTION_MASK;
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format("Error (0x%02X): %s", error, GattError.parse(error)));
				}
				loge(e.getMessage());
				if (mConnectionState == STATE_CONNECTED_AND_READY)
					try {
						logi("Sending Reset command (Op Code = 6)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
						sendLogBroadcast(LOG_LEVEL_APPLICATION, "Reset request sent");
					} catch (final Exception e1) {
						// do nothing
					}
				terminateConnection(gatt, e.getErrorNumber() /* we return the whole error number, including the error type mask */);
			}
		} finally {
			try {
				// Ensure that input stream is always closed
				mInputStream = null;
				if (is != null)
					is.close();
			} catch (final IOException e) {
				// do nothing
			}
		}
	}

	/**
	 * Opens the binary input stream that returns the firmware image content. A Path to the file is given.
	 *
	 * @param filePath the path to the HEX, BIN or ZIP file
	 * @param mimeType the file type
	 * @param mbrSize  the size of MBR, by default 0x1000
	 * @param types    the content files types in ZIP
	 * @return the input stream with binary image content
	 */
	private InputStream openInputStream(final String filePath, final String mimeType, final int mbrSize, final int types) throws IOException {
		final InputStream is = new FileInputStream(filePath);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ArchiveInputStream(is, mbrSize, types);
		if (filePath.toLowerCase(Locale.US).endsWith("hex"))
			return new HexInputStream(is, mbrSize);
		return is;
	}

	/**
	 * Opens the binary input stream. A Uri to the stream is given.
	 *
	 * @param stream   the Uri to the stream
	 * @param mimeType the file type
	 * @param mbrSize  the size of MBR, by default 0x1000
	 * @param types    the content files types in ZIP
	 * @return the input stream with binary image content
	 */
	private InputStream openInputStream(final Uri stream, final String mimeType, final int mbrSize, final int types) throws IOException {
		final InputStream is = getContentResolver().openInputStream(stream);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ArchiveInputStream(is, mbrSize, types);

		final String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
		final Cursor cursor = getContentResolver().query(stream, projection, null, null, null);
		try {
			if (cursor.moveToNext()) {
				final String fileName = cursor.getString(0 /* DISPLAY_NAME*/);

				if (fileName.toLowerCase(Locale.US).endsWith("hex"))
					return new HexInputStream(is, mbrSize);
			}
		} finally {
			cursor.close();
		}
		return is;
	}

	/**
	 * Opens the binary input stream that returns the firmware image content. A resource id in the res/raw is given.
	 *
	 * @param resId the if of the resource file
	 * @param mimeType the file type
	 * @param mbrSize  the size of MBR, by default 0x1000
	 * @param types    the content files types in ZIP
	 * @return the input stream with binary image content
	 */
	private InputStream openInputStream(final int resId, final String mimeType, final int mbrSize, final int types) throws IOException {
		final InputStream is = getResources().openRawResource(resId);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ArchiveInputStream(is, mbrSize, types);
		is.mark(2);
		int firstByte = is.read();
		is.reset();
		if (firstByte == ':')
			return new HexInputStream(is, mbrSize);
		return is;
	}

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with given progress state to the activity.
	 *
	 * @param info the current progress information
	 */
	/* package */ void updateProgressNotification(final DfuProgressInfo info) {
		final int progress = info.getProgress();
		if (mLastProgress == progress)
			return;

		mLastProgress = progress;

		// send progress or error broadcast
		sendProgressBroadcast(info);

		// the notification may not be refreshed too quickly as the ABORT button becomes not clickable
		final long now = SystemClock.elapsedRealtime();
		if (now - mLastNotificationTime < 250)
			return;
		mLastNotificationTime = now;

		if (mDisableNotification)
			return;

		// create or update notification:
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(android.R.drawable.stat_sys_upload).setOnlyAlertOnce(true);//.setLargeIcon(largeIcon);
		// Android 5
		builder.setColor(Color.GRAY);

		switch (progress) {
			case PROGRESS_CONNECTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_connecting)).setContentText(getString(R.string.dfu_status_connecting_msg, deviceName)).setProgress(100, 0, true);
				break;
			case PROGRESS_STARTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_starting)).setContentText(getString(R.string.dfu_status_starting_msg)).setProgress(100, 0, true);
				break;
			case PROGRESS_ENABLING_DFU_MODE:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_switching_to_dfu)).setContentText(getString(R.string.dfu_status_switching_to_dfu_msg))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_VALIDATING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_validating)).setContentText(getString(R.string.dfu_status_validating_msg)).setProgress(100, 0, true);
				break;
			case PROGRESS_DISCONNECTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_disconnecting)).setContentText(getString(R.string.dfu_status_disconnecting_msg, deviceName))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_COMPLETED:
				builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_completed)).setSmallIcon(android.R.drawable.stat_sys_upload_done)
						.setContentText(getString(R.string.dfu_status_completed_msg)).setAutoCancel(true).setColor(0xFF00B81A);
				break;
			case PROGRESS_ABORTED:
				builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_aborted)).setSmallIcon(android.R.drawable.stat_sys_upload_done)
						.setContentText(getString(R.string.dfu_status_aborted_msg)).setAutoCancel(true);
				break;
			default:
					// progress is in percents
					final String title = info.getTotalParts() == 1 ? getString(R.string.dfu_status_uploading) : getString(R.string.dfu_status_uploading_part, info.getCurrentPart(), info.getTotalParts());
					final String text = getString(R.string.dfu_status_uploading_msg, deviceName);
					builder.setOngoing(true).setContentTitle(title).setContentText(text).setProgress(100, progress, false);
		}

		// update the notification
		final Intent intent = new Intent(this, getNotificationTarget());
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(EXTRA_PROGRESS, info.getProgress());
		final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pendingIntent);

		// Add Abort action to the notification
		if (progress != PROGRESS_ABORTED && progress != PROGRESS_COMPLETED) {
			final Intent abortIntent = new Intent(BROADCAST_ACTION);
			abortIntent.putExtra(EXTRA_ACTION, ACTION_ABORT);
			final PendingIntent pendingAbortIntent = PendingIntent.getBroadcast(this, 1, abortIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(R.drawable.ic_action_notify_cancel, getString(R.string.dfu_action_abort), pendingAbortIntent);
		}

		final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(NOTIFICATION_ID, builder.build());
	}

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with given error numbre to the activity.
	 *
	 * @param error the error number
	 */
	/* package */ void updateProgressNotification(final int error) {
		sendErrorBroadcast(error);

		if (mDisableNotification)
			return;

		// create or update notification:
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
				.setSmallIcon(android.R.drawable.stat_sys_upload)
				.setOnlyAlertOnce(true)
				.setColor(Color.RED)
				.setOngoing(false)
				.setContentTitle(getString(R.string.dfu_status_error))
				.setSmallIcon(android.R.drawable.stat_sys_upload_done)
				.setContentText(getString(R.string.dfu_status_error_msg))
				.setAutoCancel(true);

		// update the notification
		final Intent intent = new Intent(this, getNotificationTarget());
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(EXTRA_PROGRESS, error); // this may contains ERROR_CONNECTION_MASK bit!
		final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pendingIntent);

		final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		manager.notify(NOTIFICATION_ID, builder.build());
	}

	/**
	 * This method must return the activity class that will be used to create the pending intent used as a content intent in the notification showing the upload progress.
	 * The activity will be launched when user click the notification. DfuService will add {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK} flag and the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - target device address</li>
	 * <li>{@link #EXTRA_DEVICE_NAME} - target device name</li>
	 * <li>{@link #EXTRA_PROGRESS} - the connection state (values < 0)*, current progress (0-100) or error number if {@link #ERROR_MASK} bit set.</li>
	 * </ul>
	 * <p>
	 * __________<br />
	 * * - connection state constants:
	 * <ul>
	 * <li>{@link #PROGRESS_CONNECTING}</li>
	 * <li>{@link #PROGRESS_DISCONNECTING}</li>
	 * <li>{@link #PROGRESS_COMPLETED}</li>
	 * <li>{@link #PROGRESS_ABORTED}</li>
	 * <li>{@link #PROGRESS_STARTING}</li>
	 * <li>{@link #PROGRESS_ENABLING_DFU_MODE}</li>
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * </ul>
	 * </p>
	 *
	 * @return the target activity class
	 */
	protected abstract Class<? extends Activity> getNotificationTarget();

	private void sendProgressBroadcast(final DfuProgressInfo info) {
		final long now = SystemClock.elapsedRealtime();
		final float speed = now - mLastProgressTime != 0 ? (float) (info.getBytesSent() - mLastBytesSent) / (float) (now - mLastProgressTime) : 0.0f;
		final float avgSpeed = now - mStartTime != 0 ? (float) info.getBytesSent() / (float) (now - mStartTime) : 0.0f;
		mLastProgressTime = now;
		mLastBytesSent = info.getBytesSent();

		final Intent broadcast = new Intent(BROADCAST_PROGRESS);
		broadcast.putExtra(EXTRA_DATA, info.getProgress());
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		broadcast.putExtra(EXTRA_PART_CURRENT, info.getCurrentPart());
		broadcast.putExtra(EXTRA_PARTS_TOTAL, info.getTotalParts());
		broadcast.putExtra(EXTRA_SPEED_B_PER_MS, speed);
		broadcast.putExtra(EXTRA_AVG_SPEED_B_PER_MS, avgSpeed);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	/* package */ void sendErrorBroadcast(final int error) {
		final Intent broadcast = new Intent(BROADCAST_ERROR);
		if ((error & ERROR_CONNECTION_MASK) > 0) {
			broadcast.putExtra(EXTRA_DATA, error & ~ERROR_CONNECTION_MASK);
			broadcast.putExtra(EXTRA_ERROR_TYPE, ERROR_TYPE_COMMUNICATION);
		} else if ((error & ERROR_CONNECTION_STATE_MASK) > 0) {
			broadcast.putExtra(EXTRA_DATA, error & ~ERROR_CONNECTION_STATE_MASK);
			broadcast.putExtra(EXTRA_ERROR_TYPE, ERROR_TYPE_COMMUNICATION_STATE);
		} else if ((error & ERROR_REMOTE_MASK) > 0) {
			broadcast.putExtra(EXTRA_DATA, error);
			broadcast.putExtra(EXTRA_ERROR_TYPE, ERROR_TYPE_DFU_REMOTE);
		} else {
			broadcast.putExtra(EXTRA_DATA, error);
			broadcast.putExtra(EXTRA_ERROR_TYPE, ERROR_TYPE_OTHER);
		}
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	/* package */ void sendLogBroadcast(final int level, final String message) {
		final String fullMessage = "[DFU] " + message;
		final Intent broadcast = new Intent(BROADCAST_LOG);
		broadcast.putExtra(EXTRA_LOG_MESSAGE, fullMessage);
		broadcast.putExtra(EXTRA_LOG_LEVEL, level);
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	/**
	 * Initializes bluetooth adapter
	 *
	 * @return <code>true</code> if initialization was successful
	 */
	private boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter through
		// BluetoothManager.
		final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
		if (bluetoothManager == null) {
			loge("Unable to initialize BluetoothManager.");
			return false;
		}

		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			loge("Unable to obtain a BluetoothAdapter.");
			return false;
		}

		return true;
	}

	private void loge(final String message) {
        Log.e(TAG, message);
	}

	private void loge(final String message, final Throwable e) {
		Log.e(TAG, message, e);
	}

	private void logw(final String message) {
		if (BuildConfig.DEBUG)
			Log.w(TAG, message);
	}

	private void logi(final String message) {
		if (BuildConfig.DEBUG)
			Log.i(TAG, message);
	}

	private void logd(final String message) {
		if (BuildConfig.DEBUG)
			Log.d(TAG, message);
	}
}
