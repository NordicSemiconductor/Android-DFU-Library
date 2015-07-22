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

import android.annotation.SuppressLint;
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
import android.os.Build;
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
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.HexInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.internal.exception.RemoteDfuException;
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

	/**
	 * The address of the device to update.
	 */
	public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_ADDRESS";
	/**
	 * The optional device name. This name will be shown in the notification.
	 */
	public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_NAME";
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
	 * file (line 56: <code>#define DFU_APP_DATA_RESERVED 0x0000</code>) to preserve some pages. The BLE_APP_HRM_DFU sample app stores the LTK and System Attributes in the first
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
	 * Thrown then input file is not a valid HEX or ZIP file.
	 */
	public static final int ERROR_FILE_INVALID = ERROR_MASK | 0x03;
	/**
	 * Thrown when {@link java.io.IOException} occurred when reading from file.
	 */
	public static final int ERROR_FILE_IO_EXCEPTION = ERROR_MASK | 0x04;
	/**
	 * Error thrown then {@code gatt.discoverServices();} returns false.
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
	 * Flag set then the DFU target returned a DFU error. Look for DFU specification to get error codes.
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
	private static final UUID GENERIC_ATTRIBUTE_SERVICE_UUID = new UUID(0x0000180100001000l, 0x800000805F9B34FBl);
	private static final UUID SERVICE_CHANGED_UUID = new UUID(0x00002A0500001000l, 0x800000805F9B34FBl);
	private static final UUID DFU_SERVICE_UUID = new UUID(0x000015301212EFDEl, 0x1523785FEABCD123l);
	private static final UUID DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEl, 0x1523785FEABCD123l);
	private static final UUID DFU_PACKET_UUID = new UUID(0x000015321212EFDEl, 0x1523785FEABCD123l);
	private static final UUID DFU_VERSION = new UUID(0x000015341212EFDEl, 0x1523785FEABCD123l);
	private static final UUID CLIENT_CHARACTERISTIC_CONFIG = new UUID(0x0000290200001000l, 0x800000805f9b34fbl);
	//
	public static final int NOTIFICATION_ID = 283; // a random number
	private static final int NOTIFICATIONS = 1;
	private static final int INDICATIONS = 2;
	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
	private static final int MAX_PACKET_SIZE = 20; // the maximum number of bytes in one packet is 20. May be less.
	private final byte[] mBuffer = new byte[MAX_PACKET_SIZE];
	/**
	 * Lock used in synchronization purposes
	 */
	private final Object mLock = new Object();
	private BluetoothAdapter mBluetoothAdapter;
	private InputStream mInputStream;
	private String mDeviceAddress;
	private String mDeviceName;
	/**
	 * The current connection state. If its value is > 0 than an error has occurred. Error number is a negative value of mConnectionState
	 */
	private int mConnectionState;
	private final static int STATE_DISCONNECTED = 0;
	private final static int STATE_CONNECTING = -1;
	private final static int STATE_CONNECTED = -2;
	private final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
	private final static int STATE_DISCONNECTING = -4;
	private final static int STATE_CLOSED = -5;
	/**
	 * The number of the last error that has occurred or 0 if there was no error
	 */
	private int mError;
	/**
	 * Flag set when we got confirmation from the device that notifications are enabled.
	 */
	private boolean mNotificationsEnabled;
	/**
	 * Flag set when we got confirmation from the device that Service Changed indications are enabled.
	 */
	private boolean mServiceChangedIndicationsEnabled;
	/**
	 * The number of packets of firmware data to be send before receiving a new Packets receipt notification. 0 disables the packets notifications
	 */
	private int mPacketsBeforeNotification = 10;
	/**
	 * Size of BIN content of all hex files that are going to be transmitted.
	 */
	private int mImageSizeInBytes;
	/**
	 * Number of bytes transmitted.
	 */
	private int mBytesSent;
	/**
	 * Number of bytes confirmed by the notification.
	 */
	@SuppressWarnings("unused")
	private int mBytesConfirmed;
	private int mPacketsSentSinceNotification;
	/**
	 * This value is used to calculate the current transfer speed.
	 */
	private int mLastBytesSent;
	/**
	 * Firmware update may require two connections: one for Soft Device and/or Bootloader upload and second for Application. This fields contains the current part number.
	 */
	private int mPartCurrent;
	/**
	 * Total number of parts.
	 */
	private int mPartsTotal;
	private int mFileType;
	private long mLastProgressTime, mStartTime;
	/**
	 * Flag sent when a request has been sent that will cause the DFU target to reset. Often, after sending such command, Android throws a connection state error. If this flag is set the error will be
	 * ignored.
	 */
	private boolean mResetRequestSent;
	/**
	 * Flag indicating whether the image size has been already transferred or not
	 */
	private boolean mImageSizeSent;
	/**
	 * Flag indicating whether the init packet has been already transferred or not
	 */
	private boolean mInitPacketSent;
	/**
	 * Flag indicating whether the request was completed or not
	 */
	private boolean mRequestCompleted;
	/**
	 * <p>
	 * Flag set to <code>true</code> when the DFU target had send a notification with status other than {@link #DFU_STATUS_SUCCESS}. Setting it to <code>true</code> will abort sending firmware and
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
	 * Additionally, sometimes after writing the command 6 ({@link #OP_CODE_RESET}), Android will receive a notification and update the characteristic value with 10-03-02 and the callback for write
	 * reset command will log "[DFU] Data written to ..., value (0x): 10-03-02" instead of "...(x0): 06". But this does not matter for the DFU process.
	 * </p>
	 * <p>
	 * N* - Value of Packet Receipt Notification, 10 by default.
	 * </p>
	 */
	private boolean mRemoteErrorOccurred;
	/** Flag set to true if sending was paused. */
	private boolean mPaused;
	/** Flag set to true if sending was aborted. */
	private boolean mAborted;
	/**
	 * Latest data received from device using notification.
	 */
	private byte[] mReceivedData = null;

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

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	};

	private final BroadcastReceiver mDfuActionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int action = intent.getIntExtra(EXTRA_ACTION, 0);

			switch (action) {
				case ACTION_PAUSE:
					mPaused = true;
					break;
				case ACTION_RESUME:
					mPaused = false;

					// Notify waiting thread
					synchronized (mLock) {
						mLock.notifyAll();
					}
					break;
				case ACTION_ABORT:
					mPaused = false;
					mAborted = true;

					// Notify waiting thread
					synchronized (mLock) {
						mLock.notifyAll();
					}
					break;
			}
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

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}
	};

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			// Check whether an error occurred
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					logi("Connected to GATT server");
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
								wait(1600);

								// After 1.6s the services are already discovered so the following gatt.discoverServices() finishes almost immediately.

								// NOTE: This also works with shorted waiting time. The gatt.discoverServices() must be called after the indication is received which is
								// about 600ms after establishing connection. Values 600 - 1600ms should be OK.
							}
						} catch (InterruptedException e) {
							// Do nothing
						}
					}

					// Attempts to discover services after successful connection.
					final boolean success = gatt.discoverServices();
					logi("Attempting to start service discovery... " + (success ? "succeed" : "failed"));

					if (!success) {
						mError = ERROR_SERVICE_DISCOVERY_NOT_STARTED;
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
				mPaused = false;
				mError = ERROR_CONNECTION_STATE_MASK | status;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				logi("Services discovered");
				mConnectionState = STATE_CONNECTED_AND_READY;
			} else {
				loge("Service discovery error: " + status);
				mError = ERROR_CONNECTION_MASK | status;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		@Override
		public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
					if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
						// We have enabled indications for the Service Changed characteristic
						mServiceChangedIndicationsEnabled = descriptor.getValue()[0] == 2;
						mRequestCompleted = true;
					}
				}
			} else {
				loge("Descriptor read error: " + status);
				mError = ERROR_CONNECTION_MASK | status;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		@Override
		public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
					if (SERVICE_CHANGED_UUID.equals(descriptor.getCharacteristic().getUuid())) {
						// We have enabled indications for the Service Changed characteristic
						mServiceChangedIndicationsEnabled = descriptor.getValue()[0] == 2;
					} else {
						// We have enabled notifications for this characteristic
						mNotificationsEnabled = descriptor.getValue()[0] == 1;
					}
				}
			} else {
				loge("Descriptor write error: " + status);
				mError = ERROR_CONNECTION_MASK | status;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
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
				if (DFU_PACKET_UUID.equals(characteristic.getUuid())) {
					if (mImageSizeSent && mInitPacketSent) {
						// If the PACKET characteristic was written with image data, update counters
						mBytesSent += characteristic.getValue().length;
						mPacketsSentSinceNotification++;

						// If a packet receipt notification is expected, or the last packet was sent, do nothing. There onCharacteristicChanged listener will catch either
						// a packet confirmation (if there are more bytes to send) or the image received notification (it upload process was completed)
						final boolean notificationExpected = mPacketsBeforeNotification > 0 && mPacketsSentSinceNotification == mPacketsBeforeNotification;
						final boolean lastPacketTransferred = mBytesSent == mImageSizeInBytes;

						if (notificationExpected || lastPacketTransferred)
							return;

						// When neither of them is true, send the next packet
						try {
							waitIfPaused();
							// The writing might have been aborted (mAborted = true), an error might have occurred.
							// In that case stop sending.
							if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
								// notify waiting thread
								synchronized (mLock) {
									sendLogBroadcast(LOG_LEVEL_WARNING, "Upload terminated");
									mLock.notifyAll();
									return;
								}
							}

							final byte[] buffer = mBuffer;
							final int size = mInputStream.read(buffer);
							writePacket(gatt, characteristic, buffer, size);
							updateProgressNotification();
							return;
						} catch (final HexFileValidationException e) {
							loge("Invalid HEX file");
							mError = ERROR_FILE_INVALID;
						} catch (final IOException e) {
							loge("Error while reading the input stream", e);
							mError = ERROR_FILE_IO_EXCEPTION;
						}
					} else if (!mImageSizeSent) {
						// We've got confirmation that the image size was sent
						sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
						mImageSizeSent = true;
					} else {
						// We've got confirmation that the init packet was sent
						sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
						mInitPacketSent = true;
					}
				} else {
					// If the CONTROL POINT characteristic was written just set the flag to true. The main thread will continue its task when notified.
					sendLogBroadcast(LOG_LEVEL_INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
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
					mError = ERROR_CONNECTION_MASK | status;
				}
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		@Override
		public void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				/*
				 * This method is called when the DFU Version characteristic has been read.
				 */
				sendLogBroadcast(LOG_LEVEL_INFO, "Read Response received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
				mReceivedData = characteristic.getValue();
				mRequestCompleted = true;
			} else {
				loge("Characteristic read error: " + status);
				mError = ERROR_CONNECTION_MASK | status;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		@Override
		public void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
			final int responseType = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);

			switch (responseType) {
				case OP_CODE_PACKET_RECEIPT_NOTIF_KEY:
					final BluetoothGattCharacteristic packetCharacteristic = gatt.getService(DFU_SERVICE_UUID).getCharacteristic(DFU_PACKET_UUID);

					try {
						mBytesConfirmed = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32, 1);
						mPacketsSentSinceNotification = 0;

						waitIfPaused();
						// The writing might have been aborted (mAborted = true), an error might have occurred.
						// In that case quit sending.
						if (mAborted || mError != 0 || mRemoteErrorOccurred || mResetRequestSent) {
							sendLogBroadcast(LOG_LEVEL_WARNING, "Upload terminated");
							break;
						}

						final byte[] buffer = mBuffer;
						final int size = mInputStream.read(buffer);
						writePacket(gatt, packetCharacteristic, buffer, size);
						updateProgressNotification();
						return;
					} catch (final HexFileValidationException e) {
						loge("Invalid HEX file");
						mError = ERROR_FILE_INVALID;
					} catch (final IOException e) {
						loge("Error while reading the input stream", e);
						mError = ERROR_FILE_IO_EXCEPTION;
					}
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

					sendLogBroadcast(LOG_LEVEL_INFO, "Notification received from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
					mReceivedData = characteristic.getValue();
					break;
			}

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
		}

		// This method is repeated here and in the service class for performance matters.
		private String parse(final BluetoothGattCharacteristic characteristic) {
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
	/**
	 * Stores the last progress percent. Used to prevent from sending progress notifications with the same value.
	 * @see #updateProgressNotification(int)
	 */
	private int mLastProgress = -1;

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

		final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mConnectionStateBroadcastReceiver, filter);

		final IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		registerReceiver(mBondStateBroadcastReceiver, bondFilter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.unregisterReceiver(mDfuActionReceiver);

		unregisterReceiver(mDfuActionReceiver);
		unregisterReceiver(mConnectionStateBroadcastReceiver);
		unregisterReceiver(mBondStateBroadcastReceiver);
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

		// Read input parameters
		final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
		final String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
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

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Starting DFU service");

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
				// Update the file type bit field basing on the ZIP content
				if (fileType == TYPE_AUTO && MIME_TYPE_ZIP.equals(mimeType)) {
					final ArchiveInputStream zhis = (ArchiveInputStream) is;
					fileType = zhis.getContentType();
				}
				mFileType = fileType;
				// Set the Init packet stream in case of a ZIP file
				if (MIME_TYPE_ZIP.equals(mimeType)) {
					final ArchiveInputStream zhis = (ArchiveInputStream) is;
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
			} catch (final IOException e) {
				loge("An exception occurred while calculating file size", e);
				updateProgressNotification(ERROR_FILE_ERROR);
				return;
			} catch (final Exception e) {
				loge("An exception occurred while opening files. Did you set the firmware file?", e);
				updateProgressNotification(ERROR_FILE_ERROR);
				return;
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

			sendLogBroadcast(LOG_LEVEL_INFO, "Connected. Services discovered");
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
				}

				/*
				 *  Check if we are in the DFU Bootloader or in the Application that supports the buttonless update.
				 *
				 *  In the DFU from SDK 6.1, which was also supporting the buttonless update, there was no DFU Version characteristic. In that case we may find out whether
				 *  we are in the bootloader or application by simply checking the number of characteristics.
				 */
				if (version == 1 || (version == 0 && gatt.getServices().size() > 3 /* No DFU Version char but more services than Generic Access, Generic Attribute, DFU Service */)) {
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

										updateProgressNotification(PROGRESS_DISCONNECTING);
										sendLogBroadcast(LOG_LEVEL_VERBOSE, "Disconnecting...");
										gatt.disconnect();
										waitUntilDisconnected();
										sendLogBroadcast(LOG_LEVEL_INFO, "Disconnected");

										// Close the device
										close(gatt);

										logi("Restarting service");
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
	 * Connects to the BLE device with given address. This method is SYNCHRONOUS, it wait until the connection status change from {@link #STATE_CONNECTING} to {@link #STATE_CONNECTED_AND_READY} or an
	 * error occurs. This method returns <code>null</code> if Bluetooth adapter is disabled.
	 *
	 * @param address the device address
	 * @return the GATT device or <code>null</code> if Bluetooth adapter is disabled.
	 */
	private BluetoothGatt connect(final String address) {
		if (!mBluetoothAdapter.isEnabled())
			return null;

		mConnectionState = STATE_CONNECTING;

		logi("Connecting to the device...");
		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		final BluetoothGatt gatt = device.connectGatt(this, false, mGattCallback);

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
	private void terminateConnection(final BluetoothGatt gatt, final int error) {
		if (mConnectionState != STATE_DISCONNECTED) {
			updateProgressNotification(PROGRESS_DISCONNECTING);

			// No need to disable notifications

			// Disconnect from the device
			disconnect(gatt);
			sendLogBroadcast(LOG_LEVEL_INFO, "Disconnected");
		}

		// Close the device
		refreshDeviceCache(gatt, false); // This should be set to true when DFU Version is 0.5 or lower
		close(gatt);
		updateProgressNotification(error);
	}

	/**
	 * Disconnects from the device. This is SYNCHRONOUS method and waits until the callback returns new state. Terminates immediately if device is already disconnected. Do not call this method
	 * directly, use {@link #terminateConnection(android.bluetooth.BluetoothGatt, int)} instead.
	 *
	 * @param gatt the GATT device that has to be disconnected
	 */
	private void disconnect(final BluetoothGatt gatt) {
		if (mConnectionState == STATE_DISCONNECTED)
			return;

		mConnectionState = STATE_DISCONNECTING;

		logi("Disconnecting from the device...");
		gatt.disconnect();

		// We have to wait until device gets disconnected or an error occur
		waitUntilDisconnected();
	}

	/**
	 * Wait until the connection state will change to {@link #STATE_DISCONNECTED} or until an error occurs.
	 */
	private void waitUntilDisconnected() {
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
	private void close(final BluetoothGatt gatt) {
		logi("Cleaning up...");
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.close()");
		gatt.close();
		mConnectionState = STATE_CLOSED;
	}

	/**
	 * Clears the device cache. After uploading new firmware the DFU target will have other services than before.
	 *
	 * @param gatt  the GATT device to be refreshed
	 * @param force <code>true</code> to force the refresh
	 */
	private void refreshDeviceCache(final BluetoothGatt gatt, final boolean force) {
		/*
		 * If the device is bonded this is up to the Service Changed characteristic to notify Android that the services has changed.
		 * There is no need for this trick in that case.
		 * If not bonded, the Android should not keep the services cached when the Service Changed characteristic is present in the target device database.
		 * However, due to the Android bug (still exists in Android 5.0.1), it is keeping them anyway and the only way to clear services is by using this hidden refresh method.
		 */
		if (force || gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.refresh()");
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
				sendLogBroadcast(LOG_LEVEL_WARNING, "Refreshing failed");
			}
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
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Reading DFU version number...");

		gatt.readCharacteristic(characteristic);

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
			throw new DfuException("Unable to read version number", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read version number", mConnectionState);

		// The version is a 16-bit unsigned int
		return characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);
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
	private void enableCCCD(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int type) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		final String debugString = type == NOTIFICATIONS ? "notifications" : "indications";
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to set " + debugString + " state", mConnectionState);

		mReceivedData = null;
		mError = 0;
		if ((type == NOTIFICATIONS && mNotificationsEnabled) || (type == INDICATIONS && mServiceChangedIndicationsEnabled))
			return;

		logi("Enabling " + debugString + "...");
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Enabling " + debugString + " for " + characteristic.getUuid());

		// enable notifications locally
		gatt.setCharacteristicNotification(characteristic, true);

		// enable notifications on the device
		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
		descriptor.setValue(type == NOTIFICATIONS ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.writeDescriptor(" + descriptor.getUuid() + (type == NOTIFICATIONS ? ", value=0x01-00)" : ", value=0x02-00)"));
		gatt.writeDescriptor(descriptor);

		// We have to wait until device receives a response or an error occur
		try {
			synchronized (mLock) {
				while ((((type == NOTIFICATIONS && !mNotificationsEnabled) || (type == INDICATIONS && !mServiceChangedIndicationsEnabled))
						&& mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
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
	 * @param characteristic the Service Changed characteristic
	 * @return <code>true</code> if Service Changed CCCD is enabled ans set to INDICATE
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private boolean isServiceChangedCCCDEnabled(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to read Service Changed CCCD", mConnectionState);
		// If the Service Changed characteristic or the CCCD is not available we return false.
		if (characteristic == null)
			return false;

		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
		if (descriptor == null)
			return false;

		mRequestCompleted = false;
		mError = 0;

		logi("Reading Service Changed CCCD value...");
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Reading Service Changed CCCD value...");

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

		return mServiceChangedIndicationsEnabled;
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
	private void writeOpCode(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value, final boolean reset) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		mReceivedData = null;
		mError = 0;
		mRequestCompleted = false;
		/*
		 * Sending a command that will make the DFU target to reboot may cause an error 133 (0x85 - Gatt Error). If so, with this flag set, the error will not be shown to the user
		 * as the peripheral is disconnected anyway. See: mGattCallback#onCharacteristicWrite(...) method
		 */
		mResetRequestSent = reset;

		characteristic.setValue(value);
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
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
		mImageSizeSent = false;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[4]);
		characteristic.setValue(imageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((!mImageSizeSent && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
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
		mImageSizeSent = false;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[12]);
		characteristic.setValue(softDeviceImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		characteristic.setValue(bootloaderImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 4);
		characteristic.setValue(appImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 8);
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((!mImageSizeSent && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
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

	/**
	 * Writes the Init packet to the characteristic. This method is SYNCHRONOUS and wait until the {@link android.bluetooth.BluetoothGattCallback#onCharacteristicWrite(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param buffer         the init packet as a byte array. This must be shorter or equal to 20 bytes (TODO check this restriction).
	 * @param size           the init packet size
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeInitPacket(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] buffer, final int size) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		byte[] locBuffer = buffer;
		if (buffer.length != size) {
			locBuffer = new byte[size];
			System.arraycopy(buffer, 0, locBuffer, 0, size);
		}
		mReceivedData = null;
		mError = 0;
		mInitPacketSent = false;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(locBuffer);
		logi("Sending init packet (Value = " + parse(locBuffer) + ")");
		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((!mInitPacketSent && mConnectionState == STATE_CONNECTED_AND_READY && mError == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mError != 0)
			throw new DfuException("Unable to write Init DFU Parameters", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Init DFU Parameters", mConnectionState);
	}

	/**
	 * Starts sending the data. This method is SYNCHRONOUS and terminates when the whole file will be uploaded or the connection status will change from {@link #STATE_CONNECTED_AND_READY}. If
	 * connection state will change, or an error will occur, an exception will be thrown.
	 *
	 * @param gatt                 the GATT device (DFU target)
	 * @param packetCharacteristic the characteristic to write file content to. Must be the DFU PACKET
	 * @return The response value received from notification with Op Code = 3 when all bytes will be uploaded successfully.
	 * @throws DeviceDisconnectedException Thrown when the device will disconnect in the middle of the transmission. The error core will be saved in {@link #mConnectionState}.
	 * @throws DfuException                Thrown if DFU error occur
	 * @throws UploadAbortedException
	 */
	private byte[] uploadFirmwareImage(final BluetoothGatt gatt, final BluetoothGattCharacteristic packetCharacteristic, final InputStream inputStream) throws DeviceDisconnectedException,
			DfuException, UploadAbortedException {
		mReceivedData = null;
		mError = 0;

		final byte[] buffer = mBuffer;
		try {
			final int size = inputStream.read(buffer);
			sendLogBroadcast(LOG_LEVEL_VERBOSE, "Sending firmware to characteristic " + packetCharacteristic.getUuid() + "...");
			writePacket(gatt, packetCharacteristic, buffer, size);
		} catch (final HexFileValidationException e) {
			throw new DfuException("HEX file not valid", ERROR_FILE_INVALID);
		} catch (final IOException e) {
			throw new DfuException("Error while reading file", ERROR_FILE_IO_EXCEPTION);
		}

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
			throw new DfuException("Uploading Firmware Image failed", mError);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Uploading Firmware Image failed: device disconnected", mConnectionState);

		return mReceivedData;
	}

	/**
	 * Writes the buffer to the characteristic. The maximum size of the buffer is 20 bytes. This method is ASYNCHRONOUS and returns immediately after adding the data to TX queue.
	 *
	 * @param gatt           the GATT device
	 * @param characteristic the characteristic to write to. Should be the DFU PACKET
	 * @param buffer         the buffer with 1-20 bytes
	 * @param size           the number of bytes from the buffer to send
	 */
	private void writePacket(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] buffer, final int size) {
		byte[] locBuffer = buffer;
		if (buffer.length != size) {
			locBuffer = new byte[size];
			System.arraycopy(buffer, 0, locBuffer, 0, size);
		}
		characteristic.setValue(locBuffer);
		gatt.writeCharacteristic(characteristic);
		// FIXME BLE buffer overflow
		// after writing to the device with WRITE_NO_RESPONSE property the onCharacteristicWrite callback is received immediately after writing data to a buffer.
		// The real sending is much slower than adding to the buffer. This method does not return false if writing didn't succeed.. just the callback is not invoked.
		//
		// More info: this works fine on Nexus 5 (Android 4.4) (4.3 seconds) and on Samsung S4 (Android 4.3) (20 seconds) so this is a driver issue.
		// Nexus 4 and 7 uses Qualcomm chip, Nexus 5 and Samsung uses Broadcom chips.
	}

	private void waitIfPaused() {
		synchronized (mLock) {
			try {
				while (mPaused)
					mLock.wait();
			} catch (final InterruptedException e) {
				loge("Sleeping interrupted", e);
			}
		}
	}

	@SuppressLint("NewApi")
	private boolean createBond(final BluetoothDevice device) {
		if (device.getBondState() == BluetoothDevice.BOND_BONDED)
			return true;

		boolean result;
		mRequestCompleted = false;

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Starting pairing...");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.getDevice().createBond()");
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

	private boolean createBondApi18(final BluetoothDevice device) {
		/*
		 * There is a createBond() method in BluetoothDevice class but for now it's hidden. We will call it using reflections. It has been revealed in KitKat (Api19)
		 */
		try {
			final Method createBond = device.getClass().getMethod("createBond");
			if (createBond != null) {
				sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.getDevice().createBond() (hidden)");
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
	private boolean removeBond(final BluetoothDevice device) {
		if (device.getBondState() == BluetoothDevice.BOND_NONE)
			return true;

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Removing bond information...");
		boolean result = false;
		/*
		 * There is a removeBond() method in BluetoothDevice class but for now it's hidden. We will call it using reflections.
		 */
		try {
			final Method removeBond = device.getClass().getMethod("removeBond");
			if (removeBond != null) {
				mRequestCompleted = false;
				sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.getDevice().removeBond() (hidden)");
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
	private byte[] readNotificationResponse() throws DeviceDisconnectedException, DfuException, UploadAbortedException {
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

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with current progress to the activity.
	 */
	private void updateProgressNotification() {
		final int progress = (int) (100.0f * mBytesSent / mImageSizeInBytes);
		if (mLastProgress == progress)
			return;

		mLastProgress = progress;
		updateProgressNotification(progress);
	}

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with given progress or error state to the activity.
	 *
	 * @param progress the current progress state or an error number, can be one of {@link #PROGRESS_CONNECTING}, {@link #PROGRESS_STARTING}, {@link #PROGRESS_ENABLING_DFU_MODE},
	 *                 {@link #PROGRESS_VALIDATING}, {@link #PROGRESS_DISCONNECTING}, {@link #PROGRESS_COMPLETED} or {@link #ERROR_FILE_ERROR}, {@link #ERROR_FILE_INVALID} , etc
	 */
	private void updateProgressNotification(final int progress) {
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		// final Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_notify_dfu); <- this looks bad on Android 5

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this).setSmallIcon(android.R.drawable.stat_sys_upload).setOnlyAlertOnce(true);//.setLargeIcon(largeIcon);
		// Android 5
		builder.setColor(Color.GRAY);

		switch (progress) {
			case PROGRESS_CONNECTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_connecting)).setContentText(getString(R.string.dfu_status_connecting_msg, deviceName)).setProgress(100, 0, true);
				break;
			case PROGRESS_STARTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_starting)).setContentText(getString(R.string.dfu_status_starting_msg, deviceName)).setProgress(100, 0, true);
				break;
			case PROGRESS_ENABLING_DFU_MODE:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_switching_to_dfu)).setContentText(getString(R.string.dfu_status_switching_to_dfu_msg, deviceName))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_VALIDATING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_validating)).setContentText(getString(R.string.dfu_status_validating_msg, deviceName)).setProgress(100, 0, true);
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
				if (progress >= ERROR_MASK) {
					// progress is an error number
					builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_error)).setSmallIcon(android.R.drawable.stat_sys_upload_done)
							.setContentText(getString(R.string.dfu_status_error_msg)).setAutoCancel(true).setColor(Color.RED);
				} else {
					// progress is in percents
					final String title = mPartsTotal == 1 ? getString(R.string.dfu_status_uploading) : getString(R.string.dfu_status_uploading_part, mPartCurrent, mPartsTotal);
					final String text = (mFileType & TYPE_APPLICATION) > 0 ? getString(R.string.dfu_status_uploading_msg, deviceName) : getString(R.string.dfu_status_uploading_components_msg, deviceName);
					builder.setOngoing(true).setContentTitle(title).setContentText(text).setProgress(100, progress, false);
				}
		}
		// send progress or error broadcast
		if (progress < ERROR_MASK)
			sendProgressBroadcast(progress);
		else
			sendErrorBroadcast(progress);

		// update the notification
		final Intent intent = new Intent(this, getNotificationTarget());
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(EXTRA_PROGRESS, progress); // this may contains ERROR_CONNECTION_MASK bit!
		final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pendingIntent);

		// Add Abort action to the notification
		if (progress != PROGRESS_ABORTED && progress != PROGRESS_COMPLETED && progress < ERROR_MASK) {
			final Intent abortIntent = new Intent(BROADCAST_ACTION);
			abortIntent.putExtra(EXTRA_ACTION, ACTION_ABORT);
			final PendingIntent pendingAbortIntent = PendingIntent.getBroadcast(this, 1, abortIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(R.drawable.ic_action_notify_cancel, getString(R.string.dfu_action_abort), pendingAbortIntent);
		}

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

	private void sendProgressBroadcast(final int progress) {
		final long now = SystemClock.elapsedRealtime();
		final float speed = now - mLastProgressTime != 0 ? (float) (mBytesSent - mLastBytesSent) / (float) (now - mLastProgressTime) : 0.0f;
		final float avgSpeed = now - mStartTime != 0 ? (float) mBytesSent / (float) (now - mStartTime) : 0.0f;
		mLastProgressTime = now;
		mLastBytesSent = mBytesSent;

		final Intent broadcast = new Intent(BROADCAST_PROGRESS);
		broadcast.putExtra(EXTRA_DATA, progress);
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		broadcast.putExtra(EXTRA_PART_CURRENT, mPartCurrent);
		broadcast.putExtra(EXTRA_PARTS_TOTAL, mPartsTotal);
		broadcast.putExtra(EXTRA_SPEED_B_PER_MS, speed);
		broadcast.putExtra(EXTRA_AVG_SPEED_B_PER_MS, avgSpeed);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	private void sendErrorBroadcast(final int error) {
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

	private void sendLogBroadcast(final int level, final String message) {
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
		if (BuildConfig.DEBUG)
			Log.e(TAG, message);
	}

	private void loge(final String message, final Throwable e) {
		if (BuildConfig.DEBUG)
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
}
