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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;

import no.nordicsemi.android.dfu.internal.ArchiveInputStream;
import no.nordicsemi.android.dfu.internal.HexInputStream;
import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.SizeValidationException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;
import no.nordicsemi.android.error.GattError;

/**
 * The DFU Service provides full support for Over-the-Air (OTA) Device Firmware Update (DFU)
 * by Nordic Semiconductor.
 * With the Soft Device 7.0.0+ it allows to upload a new Soft Device, new Bootloader and a
 * new Application. For older soft devices only the Application update is supported.
 * <p>
 * To run the service to your application extend it in your project and overwrite the missing method.
 * Remember to add your service class to the AndroidManifest.xml file.
 * <p>
 * The {@link DfuServiceInitiator} object should be used to start the DFU Service.
 * <pre>
 * final DfuServiceInitiator starter = new DfuServiceInitiator(mSelectedDevice.getAddress())
 * 		.setDeviceName(mSelectedDevice.getName())
 * 		.setKeepBond(keepBond)
 * 		.setZip(mFileStreamUri, mFilePath) // where one, URI or path, should be null
 *		.start(this, DfuService.class);
 * </pre>
 * <p>
 * You may register the progress and log listeners using the {@link DfuServiceListenerHelper}
 * helper class. See {@link DfuProgressListener} and {@link DfuLogListener} for more information.
 * <p>
 * The service will show its progress on the notification bar and will send local broadcasts to the
 * application.
 */
@SuppressWarnings("deprecation")
public abstract class DfuBaseService extends IntentService implements DfuProgressInfo.ProgressListener {
	private static final String TAG = "DfuBaseService";

	/* package */ static boolean DEBUG = false;

	public static final int NOTIFICATION_ID = 283; // a random number
	public static final String NOTIFICATION_CHANNEL_DFU = "dfu";

	/**
	 * The address of the device to update.
	 */
	public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_ADDRESS";
	/**
	 * The optional device name. This name will be shown in the notification.
	 */
	public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_NAME";
	/**
	 * A boolean indicating whether to disable the progress notification in the status bar.
	 * Defaults to false.
	 */
	public static final String EXTRA_DISABLE_NOTIFICATION = "no.nordicsemi.android.dfu.extra.EXTRA_DISABLE_NOTIFICATION";
	/**
	 * A boolean indicating whether the DFU service should be set as a foreground service.
	 * It is recommended to have it as a background service at least on Android Oreo or newer as
	 * the background service will be killed by the system few moments after the user closed the
	 * foreground app.
	 * <p>
	 * Read more here: <a href="https://developer.android.com/about/versions/oreo/background.html">https://developer.android.com/about/versions/oreo/background.html</a>
	 */
	public static final String EXTRA_FOREGROUND_SERVICE = "no.nordicsemi.android.dfu.extra.EXTRA_FOREGROUND_SERVICE";
	/**
	 * An extra private field indicating which reconnection attempt is being performed.
	 * In case of error 133 the service will retry to connect 2 more times.
	 */
	private static final String EXTRA_RECONNECTION_ATTEMPT = "no.nordicsemi.android.dfu.extra.EXTRA_RECONNECTION_ATTEMPT";
	/**
	 * An extra private field indicating which DFU attempt is being performed.
	 * If the target device will disconnect for some unknown reason during DFU, the service will
	 * retry to connect and continue. In case of Legacy DFU it will reconnect and restart process.
	 */
	/* package */ static final String EXTRA_DFU_ATTEMPT = "no.nordicsemi.android.dfu.extra.EXTRA_DFU_ATTEMPT";
	/**
	 * Maximum number of DFU attempts. Default value is 0.
	 */
	public static final String EXTRA_MAX_DFU_ATTEMPTS = "no.nordicsemi.android.dfu.extra.EXTRA_MAX_DFU_ATTEMPTS";
	/**
	 * If the new firmware (application) does not share the bond information with the old one,
	 * the bond information is lost. Set this flag to <code>true</code> to make the service create
	 * new bond with the new application when the upload is done (and remove the old one).
	 * When set to <code>false</code> (default), the DFU service assumes that the LTK is shared
	 * between them. Note: currently it is not possible to remove the old bond without creating
	 * a new one so if your old application supported bonding while the new one does not you have
	 * to modify the source code yourself.
	 * <p>
	 * In case of updating the soft device the application is always removed together with the
	 * bond information.
	 * <p>
	 * Search for occurrences of EXTRA_RESTORE_BOND in this file to check the implementation and
	 * get more details.
	 * <p>
	 * This flag is ignored when Secure DFU Buttonless Service is used.
	 * It will keep or will not restore the bond depending on the Buttonless service type.
	 */
	public static final String EXTRA_RESTORE_BOND = "no.nordicsemi.android.dfu.extra.EXTRA_RESTORE_BOND";
	/**
	 * This flag indicated whether the bond information should be kept or removed after an upgrade
	 * of the Application. If an application is being updated on a bonded device with the DFU
	 * Bootloader that has been configured to preserve the bond information for the new application,
	 * set it to <code>true</code>.
	 * <p>
	 * By default the Legacy DFU Bootloader clears the whole application's memory. It may be,
	 * however, configured in the \Nordic\nrf51\components\libraries\bootloader_dfu\dfu_types.h
	 * file (sdk 11, line 76: <code>#define DFU_APP_DATA_RESERVED 0x0000</code>) to preserve some pages.
	 * The BLE_APP_HRM_DFU sample app stores the LTK and System Attributes in the first
	 * two pages, so in order to preserve the bond information this value should be changed to
	 * 0x0800 or more. For Secure DFU this value is by default set to 3 pages.
	 * When those data are preserved, the new Application will notify the app with the
	 * Service Changed indication when launched for the first time. Otherwise this service will
	 * remove the bond information from the phone and force to refresh the device cache
	 * (see {@link #refreshDeviceCache(android.bluetooth.BluetoothGatt, boolean)}).
	 * <p>
	 * In contrast to {@link #EXTRA_RESTORE_BOND} this flag will not remove the old bonding and
	 * recreate a new one, but will keep the bond information untouched.
	 * <p>
	 * The default value of this flag is <code>false</code>.
	 * <p>
	 * This flag is ignored when Secure DFU Buttonless Service is used. It will keep or remove the
	 * bond depending on the Buttonless service type.
	 */
	public static final String EXTRA_KEEP_BOND = "no.nordicsemi.android.dfu.extra.EXTRA_KEEP_BOND";
	/**
	 * This property must contain a boolean value.
	 * <p>
	 * The {@link DfuBaseService}, when connected to a DFU target will check whether it is in
	 * application or in DFU bootloader mode. For DFU implementations from SDK 7.0 or newer
	 * this is done by reading the value of DFU Version characteristic.
	 * If the returned value is equal to 0x0100 (major = 0, minor = 1) it means that we are in the
	 * application mode and jump to the bootloader mode is required.
	 * <p>
	 * However, for DFU implementations from older SDKs, where there was no DFU Version
	 * characteristic, the service must guess. If this option is set to false (default) it will count
	 * number of device's services. If the count is equal to 3 (Generic Access, Generic Attribute,
	 * DFU Service) it will assume that it's in DFU mode. If greater than 3 - in app mode.
	 * This guessing may not be always correct. One situation may be when the nRF chip is used to
	 * flash update on external MCU using DFU. The DFU procedure may be implemented in the
	 * application, which may (and usually does) have more services.
	 * In such case set the value of this property to true.
	 */
	public static final String EXTRA_FORCE_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_FORCE_DFU";

	/**
	 * This flag indicates whether the service should scan for bootloader in Legacy DFU after
	 * switching using buttonless service. The default value is false.
	 */
	public static final String EXTRA_FORCE_SCANNING_FOR_BOOTLOADER_IN_LEGACY_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_FORCE_SCANNING_FOR_BOOTLOADER_IN_LEGACY_DFU";
	/**
	 * This options allows to disable the resume feature in Secure DFU. When the extra value is set
	 * to true, the DFU will send Init Packet and Data again, despite the firmware might have been
	 * send partially before. By default, without setting this extra, or by setting it to false,
	 * the DFU will resume the previously cancelled upload if CRC values match.
	 * <p>
	 * It is ignored when Legacy DFU is used.
	 * <p>
	 * This feature seems to help in some cases:
	 * <a href="https://github.com/NordicSemiconductor/Android-DFU-Library/issues/71">#71</a>.
	 */
	public static final String EXTRA_DISABLE_RESUME = "no.nordicsemi.android.dfu.extra.EXTRA_DISABLE_RESUME";
	/**
	 * The MBR size.
	 *
	 * @see DfuServiceInitiator#setMbrSize(int)
	 */
	public static final String EXTRA_MBR_SIZE = "no.nordicsemi.android.dfu.extra.EXTRA_MBR_SIZE";
	/**
	 * This extra allows you to control the MTU that will be requested (on Lollipop or newer devices).
	 * If the field is null, the service will not request higher MTU and will use MTU = 23
	 * (even if it has been set to a higher value before).
	 */
	public static final String EXTRA_MTU = "no.nordicsemi.android.dfu.extra.EXTRA_MTU";
	/**
	 * This extra value will be used when MTU request returned with an error. That means, that
	 * MTU has been requested before and may not be changed again. This value will be used instead.
	 */
	public static final String EXTRA_CURRENT_MTU = "no.nordicsemi.android.dfu.extra.EXTRA_CURRENT_MTU";
	/**
	 * Set this flag to true to enable experimental buttonless feature in Secure DFU from SDK 12.
	 * When the experimental Buttonless DFU Service is found on a device, the service will use it to
	 * switch the device to the bootloader mode, connect to it in that mode and proceed with DFU.
	 * <p>
	 * <b>Please, read the information below before setting it to true.</b>
	 * <p>
	 * In the SDK 12.x the Buttonless DFU feature for Secure DFU was experimental.
	 * It is NOT recommended to use it: it was not properly tested, had implementation bugs
	 * (e.g. https://devzone.nordicsemi.com/question/100609/sdk-12-bootloader-erased-after-programming/)
	 * and does not require encryption and therefore may lead to DOS attack (anyone can use it
	 * to switch the device to bootloader mode). However, as there is no other way to trigger
	 * bootloader mode on devices without a button, this DFU Library supports this service,
	 * but the feature must be explicitly enabled here.
	 * Be aware, that setting this flag to false will not protect your devices from this kind of
	 * attacks, as an attacker may use another app for that purpose. To be sure your device is
	 * secure remove this experimental service from your device.
	 * <p>
	 * <b>Spec:</b><br>
	 * Buttonless DFU Service UUID: 8E400001-F315-4F60-9FB8-838830DAEA50<br>
	 * Buttonless DFU characteristic UUID: 8E400001-F315-4F60-9FB8-838830DAEA50 (the same)<br>
	 * Enter Bootloader Op Code: 0x01<br>
	 * Correct return value: 0x20-01-01 , where:<br>
	 * 0x20 - Response Op Code<br>
	 * 0x01 - Request Code<br>
	 * 0x01 - Success<br>
	 * The device should disconnect and restart in DFU mode after sending the notification.
	 * <p>
	 * In SDK 14 this issue was fixed by Buttonless Service With Bonds.
	 */
	public static final String EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU";
	/**
	 * The duration of a delay that will be added before sending each data packet in Secure DFU,
	 * in milliseconds. This defaults to 0 for backwards compatibility reason.
	 */
	public static final String EXTRA_DATA_OBJECT_DELAY = "no.nordicsemi.android.dfu.extra.EXTRA_DATA_OBJECT_DELAY";
	/**
	 * This property must contain a boolean value.
	 * <p>
	 * If true the Packet Receipt Notification procedure will be enabled.
	 * See DFU documentation on http://infocenter.nordicsemi.com for more details.
	 * The number of packets before receiving a Packet Receipt Notification is set with property
	 * {@link #EXTRA_PACKET_RECEIPT_NOTIFICATIONS_VALUE}.
	 * The PRNs by default are enabled on devices running Android 4.3, 4.4.x and 5.x and
	 * disabled on 6.x and newer.
	 *
	 * @see #EXTRA_PACKET_RECEIPT_NOTIFICATIONS_VALUE
	 */
	public static final String EXTRA_PACKET_RECEIPT_NOTIFICATIONS_ENABLED = "no.nordicsemi.android.dfu.extra.EXTRA_PRN_ENABLED";
	/**
	 * This property must contain a positive integer value, usually from range 1-200.
	 * <p>
	 * The default value is {@link DfuServiceInitiator#DEFAULT_PRN_VALUE}.
	 * Setting it to 0 will disable the Packet Receipt Notification procedure.
	 * When sending a firmware using the DFU procedure the service will send this number of packets
	 * before waiting for a notification. Packet Receipt Notifications are used to synchronize
	 * the sender with receiver.
	 * <p>
	 * On Android, calling
	 * {@link android.bluetooth.BluetoothGatt#writeCharacteristic(BluetoothGattCharacteristic)}
	 * simply adds the packet to outgoing queue before returning the callback. Adding the next
	 * packet in the callback is much faster than the real transmission (also the speed depends on
	 * the device chip manufacturer) and the queue may reach its limit. When does, the transmission
	 * stops and Android Bluetooth hangs (see Note below). Using PRN procedure eliminates this
	 * problem as the notification is send when all packets were delivered the queue is empty.
	 * <p>
	 * Note: this bug has been fixed on Android 6.0 Marshmallow and now no notifications are required.
	 * The onCharacteristicWrite callback will be postponed until half of the queue is empty and
	 * upload will be resumed automatically. Disabling PRNs speeds up the upload process on those
	 * devices.
	 *
	 * @see #EXTRA_PACKET_RECEIPT_NOTIFICATIONS_ENABLED
	 */
	public static final String EXTRA_PACKET_RECEIPT_NOTIFICATIONS_VALUE = "no.nordicsemi.android.dfu.extra.EXTRA_PRN_VALUE";
	/**
	 * A path to the file with the new firmware. It may point to a HEX, BIN or a ZIP file.
	 * Some file manager applications return the path as a String while other return a Uri.
	 * Use the {@link #EXTRA_FILE_URI} in the later case. For files included
	 * in /res/raw resource directory please use {@link #EXTRA_FILE_RES_ID} instead.
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
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+).
	 * Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the
	 * Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_PATH = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_PATH";
	/**
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+).
	 * Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the
	 * Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_URI = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_URI";
	/**
	 * The Init packet URI. This file is required if the Extended Init Packet is required (SDK 7.0+).
	 * Must point to a 'dat' file corresponding with the selected firmware.
	 * The Init packet may contain just the CRC (in case of older versions of DFU) or the
	 * Extended Init Packet in binary format (SDK 7.0+).
	 */
	public static final String EXTRA_INIT_FILE_RES_ID = "no.nordicsemi.android.dfu.extra.EXTRA_INIT_FILE_RES_ID";
	/**
	 * The input file mime-type. Currently only "application/zip" (ZIP) or "application/octet-stream"
	 * (HEX or BIN) are supported. If this parameter is empty the "application/octet-stream" is assumed.
	 */
	public static final String EXTRA_FILE_MIME_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_MIME_TYPE";
	// Since the DFU Library version 0.5 both HEX and BIN files are supported.
	// As both files have the same MIME TYPE the distinction is made based on the file extension.
	public static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";
	public static final String MIME_TYPE_ZIP = "application/zip";
	/**
	 * This optional extra parameter may contain a file type. Currently supported are:
	 * <ul>
	 * <li>{@link #TYPE_SOFT_DEVICE} - only Soft Device update</li>
	 * <li>{@link #TYPE_BOOTLOADER} - only Bootloader update</li>
	 * <li>{@link #TYPE_APPLICATION} - only application update</li>
	 * <li>{@link #TYPE_AUTO} - the file is a ZIP file that may contain more than one HEX/BIN + DAT files.
	 * Since SDK 8.0 the ZIP Distribution packet is a recommended way of delivering firmware files.
	 * Please, see the DFU documentation for more details. A ZIP distribution packet may be created
	 * using the 'nrf util' Python application, available at
	 * <a href="https://github.com/NordicSemiconductor/pc-nrfutil">https://github.com/NordicSemiconductor/pc-nrfutil</a>.
	 * The ZIP file MAY contain only the following files: <b>softdevice.hex/bin</b>,
	 * <b>bootloader.hex/bin</b>, <b>application.hex/bin</b> to determine the type based on its name.
	 * At lease one of them MUST be present.
	 * </li>
	 * </ul>
	 * If this parameter is not provided the type is assumed as follows:
	 * <ol>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is <code>null</code> or is equal to
	 * {@value #MIME_TYPE_OCTET_STREAM} - the {@link #TYPE_APPLICATION} is assumed.</li>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is equal to {@value #MIME_TYPE_ZIP}
	 * - the {@link #TYPE_AUTO} is assumed.</li>
	 * </ol>
	 */
	public static final String EXTRA_FILE_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_TYPE";
	/**
	 * <p>
	 * The file contains a new version of Soft Device.
	 * <p>
	 * Since DFU Library 7.0 all firmware may contain an Init packet. The Init packet is required
	 * if Extended Init Packet is used by the DFU bootloader (SDK 7.0+)..
	 * The Init packet for the bootloader must be placed in the .dat file.
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_SOFT_DEVICE = 0x01;
	/**
	 * <p>
	 * The file contains a new version of Bootloader.
	 * <p>
	 * Since DFU Library 7.0 all firmware may contain an Init packet. The Init packet is required
	 * if Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * The Init packet for the bootloader must be placed in the .dat file.
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_BOOTLOADER = 0x02;
	/**
	 * <p>
	 * The file contains a new version of Application.
	 * <p>
	 * Since DFU Library 0.5 all firmware may contain an Init packet. The Init packet is required
	 * if Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * The Init packet for the application must be placed in the .dat file.
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_APPLICATION = 0x04;
	/**
	 * <p>
	 * A ZIP file that consists of more than 1 file. Since SDK 8.0 the ZIP Distribution packet is
	 * a recommended way of delivering firmware files. Please, see the DFU documentation for
	 * more details. A ZIP distribution packet may be created using the 'nrf utility' command line
	 * application, that is a part of Master Control Panel 3.8.0.
	 * For backwards compatibility this library supports also ZIP files without the manifest file.
	 * Instead they must follow the fixed naming convention:
	 * The names of files in the ZIP must be: <b>softdevice.hex</b> (or .bin), <b>bootloader.hex</b>
	 * (or .bin), <b>application.hex</b> (or .bin) in order to be read correctly. Using the
	 * Soft Device v7.0.0+ the Soft Device and Bootloader may be updated and sent together.
	 * In case of additional application file included, the service will try to send Soft Device,
	 * Bootloader and Application together (which is not supported currently) and if it fails,
	 * send first SD+BL, reconnect and send the application in the following connection.
	 * <p>
	 * Since the DFU Library 0.5 you may specify the Init packet, that will be send prior to the
	 * firmware. The init packet contains some verification data, like a device type and revision,
	 * application version or a list of supported Soft Devices. The Init packet is required if
	 * Extended Init Packet is used by the DFU bootloader (SDK 7.0+).
	 * In case of using the compatibility ZIP files the Init packet for the Soft Device and Bootloader
	 * must be in the 'system.dat' file while for the application in the 'application.dat' file
	 * (included in the ZIP). The CRC in the 'system.dat' must be a CRC of both BIN contents if
	 * both a Soft Device and a Bootloader is present.
	 *
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_AUTO = 0x00;
	/**
	 * An extra field with progress and error information used in broadcast events.
	 */
	public static final String EXTRA_DATA = "no.nordicsemi.android.dfu.extra.EXTRA_DATA";
	/**
	 * An extra field to send the progress or error information in the DFU notification.
	 * The value may contain:
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
	 * <li>An error code with {@link #ERROR_CONNECTION_MASK} if connection error occurred
	 * (e.g. GATT error (133) or Internal GATT Error (129))</li>
	 * </ul>
	 * To check if error occurred use:<br>
	 * {@code boolean error = progressValue >= DfuBaseService.ERROR_MASK;}
	 */
	public static final String EXTRA_PROGRESS = "no.nordicsemi.android.dfu.extra.EXTRA_PROGRESS";
	/**
	 * The number of currently transferred part. The SoftDevice and Bootloader may be send
	 * together as one part. If user wants to upload them together with an application it has to be
	 * sent in another connection as the second part.
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
	 * <li>{@link #EXTRA_PARTS_TOTAL} - total number of parts that are being sent, e.g. if a ZIP
	 * file contains a Soft Device, a Bootloader and an Application, the SoftDevice and Bootloader
	 * will be send together as one part. Then the service will disconnect and reconnect to the
	 * new Bootloader and send the application as part number two.</li>
	 * <li>{@link #EXTRA_SPEED_B_PER_MS} - current speed in bytes/millisecond as float</li>
	 * <li>{@link #EXTRA_AVG_SPEED_B_PER_MS} - the average transmission speed in bytes/millisecond
	 * as float</li>
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
	 * Service has triggered a switch to bootloader mode. Now the service waits for the link loss
	 * event (this may take up to several seconds) and will connect again to the same device,
	 * now started in the bootloader mode.
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
	 * <li>{@link #EXTRA_DATA} - the error number. Use {@link GattError#parse(int)} to get String
	 * representation.</li>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - the target device address</li>
	 * </ul>
	 */
	public static final String BROADCAST_ERROR = "no.nordicsemi.android.dfu.broadcast.BROADCAST_ERROR";
	/**
	 * The type of the error. This extra contains information about that kind of error has occurred.
	 * Connection state errors and other errors may share the same numbers. For example, the
	 * {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)}
	 * method may return a status code 8 (GATT INSUF AUTHORIZATION), while the status code 8
	 * returned by {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)}
	 * is a GATT CONN TIMEOUT error.
	 */
	public static final String EXTRA_ERROR_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_ERROR_TYPE";
	public static final int ERROR_TYPE_OTHER = 0;
	public static final int ERROR_TYPE_COMMUNICATION_STATE = 1;
	public static final int ERROR_TYPE_COMMUNICATION = 2;
	public static final int ERROR_TYPE_DFU_REMOTE = 3;
	/**
	 * If this bit is set than the progress value indicates an error. Use {@link GattError#parse(int)}
	 * to obtain error name.
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
	 * Thrown when the service discovery has finished but the DFU service has not been found.
	 * The device does not support DFU of is not in DFU mode.
	 */
	public static final int ERROR_SERVICE_NOT_FOUND = ERROR_MASK | 0x06;
	/**
	 * Thrown when unknown response has been obtained from the target. The DFU target must follow
	 * specification.
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
	 * DFU Bootloader version 0.6+ requires sending the Init packet. If such bootloader version is
	 * detected, but the init packet has not been set this error is thrown.
	 */
	public static final int ERROR_INIT_PACKET_REQUIRED = ERROR_MASK | 0x0B;
	/**
	 * Thrown when the firmware file is not word-aligned. The firmware size must be dividable by
	 * 4 bytes.
	 */
	public static final int ERROR_FILE_SIZE_INVALID = ERROR_MASK | 0x0C;
	/**
	 * Thrown when the received CRC does not match with the calculated one. The service will try
	 * 3 times to send the data, and if the CRC fails each time this error will be thrown.
	 */
	public static final int ERROR_CRC_ERROR = ERROR_MASK | 0x0D;
	/**
	 * Thrown when device had to be paired before the DFU process was started.
	 */
	public static final int ERROR_DEVICE_NOT_BONDED = ERROR_MASK | 0x0E;
	/**
	 * Thrown when the DFU library lost track of what is going on. Reported number of bytes is
	 * not equal to the number of bytes sent and due to some other events the library cannot recover.
	 * <p>
	 * Check https://github.com/NordicSemiconductor/Android-DFU-Library/issues/229
	 */
	public static final int ERROR_PROGRESS_LOST = ERROR_MASK | 0x0F;
	/**
	 * Flag set when the DFU target returned a DFU error. Look for DFU specification to get error
	 * codes. The error code is binary OR-ed with one of: {@link #ERROR_REMOTE_TYPE_LEGACY},
	 * {@link #ERROR_REMOTE_TYPE_SECURE} or {@link #ERROR_REMOTE_TYPE_SECURE_EXTENDED}.
	 */
	public static final int ERROR_REMOTE_MASK = 0x2000;
	public static final int ERROR_REMOTE_TYPE_LEGACY = 0x0100;
	public static final int ERROR_REMOTE_TYPE_SECURE = 0x0200;
	public static final int ERROR_REMOTE_TYPE_SECURE_EXTENDED = 0x0400;
	public static final int ERROR_REMOTE_TYPE_SECURE_BUTTONLESS = 0x0800;
	/**
	 * The flag set when one of {@link android.bluetooth.BluetoothGattCallback} methods was called
	 * with status other than {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS}.
	 */
	public static final int ERROR_CONNECTION_MASK = 0x4000;
	/**
	 * The flag set when the
	 * {@link android.bluetooth.BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * method was called with status other than {@link android.bluetooth.BluetoothGatt#GATT_SUCCESS}.
	 */
	public static final int ERROR_CONNECTION_STATE_MASK = 0x8000;
	/**
	 * The log events are only broadcast when there is no nRF Logger installed.
	 * The broadcast contains 2 extras:
	 * <ul>
	 * <li>{@link #EXTRA_LOG_LEVEL} - The log level, one of following: {@link #LOG_LEVEL_DEBUG},
	 * {@link #LOG_LEVEL_VERBOSE}, {@link #LOG_LEVEL_INFO}, {@link #LOG_LEVEL_APPLICATION},
	 * {@link #LOG_LEVEL_WARNING}, {@link #LOG_LEVEL_ERROR}</li>
	 * <li>{@link #EXTRA_LOG_MESSAGE} - The log message</li>
	 * </ul>
	 */
	public static final String BROADCAST_LOG = "no.nordicsemi.android.dfu.broadcast.BROADCAST_LOG";
	public static final String EXTRA_LOG_MESSAGE = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_INFO";
	public static final String EXTRA_LOG_LEVEL = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_LEVEL";
	/*
	 * Note:
	 * The nRF Logger API library has been excluded from the DfuLibrary.
	 * All log events are now being sent using local broadcasts and may be logged into nRF Logger
	 * in the app module. This is to make the Dfu module independent from logging tool.
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
	 * The action extra. It may have one of the following values: {@link #ACTION_PAUSE},
	 * {@link #ACTION_RESUME}, {@link #ACTION_ABORT}.
	 */
	public static final String EXTRA_ACTION = "no.nordicsemi.android.dfu.extra.EXTRA_ACTION";
	/**
	 * Pauses the upload. The service will wait for broadcasts with the action set to
	 * {@link #ACTION_RESUME} or {@link #ACTION_ABORT}.
	 */
	public static final int ACTION_PAUSE = 0;
	/** Resumes the upload that has been paused before using {@link #ACTION_PAUSE}. */
	public static final int ACTION_RESUME = 1;
	/**
	 * Aborts the upload. The service does not need to be paused before.
	 * After sending {@link #BROADCAST_ACTION} with extra {@link #EXTRA_ACTION} set to this value
	 * the DFU bootloader will restore the old application (if there was already an application).
	 * Be aware, that uploading the Soft Device will erase the application in order to make space
	 * in the memory. In case there is no application, or the application has been removed, the
	 * DFU bootloader will be started and user may try to send the application again.
	 * The bootloader may advertise with the address incremented by 1 to prevent caching services.
	 */
	public static final int ACTION_ABORT = 2;

	public static final String EXTRA_CUSTOM_UUIDS_FOR_LEGACY_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_CUSTOM_UUIDS_FOR_LEGACY_DFU";
	public static final String EXTRA_CUSTOM_UUIDS_FOR_SECURE_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_CUSTOM_UUIDS_FOR_SECURE_DFU";
	public static final String EXTRA_CUSTOM_UUIDS_FOR_EXPERIMENTAL_BUTTONLESS_DFU = "no.nordicsemi.android.dfu.extra.EXTRA_CUSTOM_UUIDS_FOR_EXPERIMENTAL_BUTTONLESS_DFU";
	public static final String EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITHOUT_BOND_SHARING = "no.nordicsemi.android.dfu.extra.EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITHOUT_BOND_SHARING";
	public static final String EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITH_BOND_SHARING = "no.nordicsemi.android.dfu.extra.EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITH_BOND_SHARING";

	/**
	 * Lock used in synchronization purposes
	 */
	private final Object mLock = new Object();
	private BluetoothAdapter mBluetoothAdapter;
	private String mDeviceAddress;
	private String mDeviceName;
	private boolean mDisableNotification;
	/**
	 * The current connection state. If its value is > 0 than an error has occurred.
	 * Error number is a negative value of mConnectionState
	 */
	protected int mConnectionState;
	protected final static int STATE_DISCONNECTED = 0;
	protected final static int STATE_CONNECTING = -1;
	protected final static int STATE_CONNECTED = -2;
	protected final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
	protected final static int STATE_DISCONNECTING = -4;
	protected final static int STATE_CLOSED = -5;
	/**
	 * The number of the last error that has occurred or 0 if there was no error
	 */
	private int mError;
	/**
	 * Stores the last progress percent. Used to prevent from sending progress notifications with
	 * the same value.
	 */
	private int mLastProgress = -1;
	/* package */ DfuProgressInfo mProgressInfo;
	private long mLastNotificationTime;

	/** Flag set to true if sending was aborted. */
	private boolean mAborted;

	private DfuCallback mDfuServiceImpl;
	private InputStream mFirmwareInputStream, mInitFileInputStream;

	private final BroadcastReceiver mDfuActionReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int action = intent.getIntExtra(EXTRA_ACTION, 0);

			logi("User action received: " + action);
			switch (action) {
				case ACTION_PAUSE:
					sendLogBroadcast(LOG_LEVEL_WARNING, "[Broadcast] Pause action received");
					if (mDfuServiceImpl != null)
						mDfuServiceImpl.pause();
					break;
				case ACTION_RESUME:
					sendLogBroadcast(LOG_LEVEL_WARNING, "[Broadcast] Resume action received");
					if (mDfuServiceImpl != null)
						mDfuServiceImpl.resume();
					break;
				case ACTION_ABORT:
					sendLogBroadcast(LOG_LEVEL_WARNING, "[Broadcast] Abort action received");
					mAborted = true;
					if (mDfuServiceImpl != null)
						mDfuServiceImpl.abort();
					break;
			}
		}
	};

	private final BroadcastReceiver mBluetoothStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
			final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_ON);
			logw("Action received: android.bluetooth.adapter.action.STATE_CHANGED [state: " + state + ", previous state: " + previousState + "]");
			if (previousState == BluetoothAdapter.STATE_ON
					&& (state == BluetoothAdapter.STATE_TURNING_OFF || state == BluetoothAdapter.STATE_OFF)) {
				sendLogBroadcast(LOG_LEVEL_WARNING, "Bluetooth adapter disabled");
				mConnectionState = STATE_DISCONNECTED;
				if (mDfuServiceImpl != null)
					mDfuServiceImpl.getGattCallback().onDisconnected();

				// Notify waiting thread
				synchronized (mLock) {
					mLock.notifyAll();
				}
			}
		}
	};

	private final BroadcastReceiver mBondStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// Obtain the device and check if this is the one that we are connected to
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (device == null || !device.getAddress().equals(mDeviceAddress))
				return;

			// Read bond state
			final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
			if (bondState == BluetoothDevice.BOND_BONDING)
				return;

			if (mDfuServiceImpl != null)
				mDfuServiceImpl.onBondStateChanged(bondState);
		}
	};

	private final BroadcastReceiver mConnectionStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// Obtain the device and check it this is the one that we are connected to
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (device == null || !device.getAddress().equals(mDeviceAddress))
				return;

			final String action = intent.getAction();

			logi("Action received: " + action);
			sendLogBroadcast(LOG_LEVEL_DEBUG, "[Broadcast] Action received: " + action);
			/*
			Handling the disconnection event here could lead to race conditions, as it also may (most probably will)
			be delivered to onConnectionStateChange below.
			See: https://github.com/NordicSemiconductor/Android-DFU-Library/issues/55

			Note: This broadcast is now received on all 3 ACL events!
				  Don't assume DISCONNECT here.

			mConnectionState = STATE_DISCONNECTED;

			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onDisconnected();

			// Notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
			}
			*/
		}
	};

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			// Check whether an error occurred
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					logi("Connected to GATT server");
					sendLogBroadcast(LOG_LEVEL_INFO, "Connected to " + mDeviceAddress);
					mConnectionState = STATE_CONNECTED;

					/*
					 * The onConnectionStateChange callback is called just after establishing connection and before sending Encryption Request BLE event in case of a paired device.
					 * In that case and when the Service Changed CCCD is enabled we will get the indication after initializing the encryption, about 1600 milliseconds later.
					 * If we discover services right after connecting, the onServicesDiscovered callback will be called immediately, before receiving the indication and the following
					 * service discovery and we may end up with old, application's services instead.
					 *
					 * This is to support the buttonless switch from application to bootloader mode where the DFU bootloader notifies the master about service change.
					 * Tested on Nexus 4 (Android 4.4.4 and 5), Nexus 5 (Android 5), Samsung Note 2 (Android 4.4.2). The time after connection to end of service discovery is about 1.6s
					 * on Samsung Note 2.
					 *
					 * NOTE: We are doing this to avoid the hack with calling the hidden gatt.refresh()
					 * method, at least for bonded devices.
					 *
					 * IMPORTANT: BluetoothDevice.getBondState() returns true if the bond information
					 * is present on Android, not necessarily when the link is established or even
					 * encrypted. This is a security issue, but in here it does not matter.
					 */
					if (gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED) {
						logi("Waiting 1600 ms for a possible Service Changed indication...");
						waitFor(1600);
						// After 1.6s the services are already discovered so the following gatt.discoverServices() finishes almost immediately.

						// NOTE: This also works with shorted waiting time. The gatt.discoverServices() must be called after the indication is received which is
						// about 600ms after establishing connection. Values 600 - 1600ms should be OK.
					}

					// Attempts to discover services after successful connection.
					sendLogBroadcast(LOG_LEVEL_VERBOSE, "Discovering services...");
					sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.discoverServices()");
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
					mConnectionState = STATE_DISCONNECTED;
					if (mDfuServiceImpl != null)
						mDfuServiceImpl.getGattCallback().onDisconnected();
				}
			} else {
				if (status == 0x08 /* GATT CONN TIMEOUT */ || status == 0x13 /* GATT CONN TERMINATE PEER USER */)
					logw("Target device disconnected with status: " + status);
				else
					loge("Connection state change error: " + status + " newState: " + newState);
				mError = ERROR_CONNECTION_STATE_MASK | status;
				if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					mConnectionState = STATE_DISCONNECTED;
					if (mDfuServiceImpl != null)
						mDfuServiceImpl.getGattCallback().onDisconnected();
				}
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

		// Other methods just pass the parameters through
		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onCharacteristicWrite(gatt, characteristic, status);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onCharacteristicRead(gatt, characteristic, status);
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onCharacteristicChanged(gatt, characteristic);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onDescriptorWrite(gatt, descriptor, status);
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onDescriptorRead(gatt, descriptor, status);
		}

		@SuppressLint("NewApi")
		@Override
		public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onMtuChanged(gatt, mtu, status);
		}

		@SuppressLint("NewApi")
		@Override
		public void onPhyUpdate(final BluetoothGatt gatt, final int txPhy, final int rxPhy, final int status) {
			if (mDfuServiceImpl != null)
				mDfuServiceImpl.getGattCallback().onPhyUpdate(gatt, txPhy, rxPhy, status);
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

		DEBUG = isDebug();
		logi("DFU service created. Version: " + BuildConfig.VERSION_NAME);
		initialize();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		final IntentFilter actionFilter = makeDfuActionIntentFilter();
		manager.registerReceiver(mDfuActionReceiver, actionFilter);
		registerReceiver(mDfuActionReceiver, actionFilter); // Additionally we must register this receiver as a non-local to get broadcasts from the notification actions

		final IntentFilter filter = new IntentFilter();
		// As we no longer perform any action based on this broadcast, we may log all ACL events
		filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mConnectionStateBroadcastReceiver, filter);

		final IntentFilter bondFilter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
		registerReceiver(mBondStateBroadcastReceiver, bondFilter);

		final IntentFilter stateFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
		registerReceiver(mBluetoothStateBroadcastReceiver, stateFilter);
	}

	@Override
	public void onTaskRemoved(final Intent rootIntent) {
		super.onTaskRemoved(rootIntent);
		// This method is called when user removed the app from Recents.
		// By default, the service will be killed and recreated immediately after that,
		// but we don't want it. User removed the task, so let's cancel DFU.
		final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.cancel(NOTIFICATION_ID);
		}
		stopSelf();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (mDfuServiceImpl != null)
			mDfuServiceImpl.abort();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.unregisterReceiver(mDfuActionReceiver);

		unregisterReceiver(mDfuActionReceiver);
		unregisterReceiver(mConnectionStateBroadcastReceiver);
		unregisterReceiver(mBondStateBroadcastReceiver);
		unregisterReceiver(mBluetoothStateBroadcastReceiver);

		try {
			// Ensure that input stream is always closed
			if (mFirmwareInputStream != null)
				mFirmwareInputStream.close();
			if (mInitFileInputStream != null)
				mInitFileInputStream.close();
		} catch (final IOException e) {
			// do nothing
		} finally {
			mFirmwareInputStream = null;
			mInitFileInputStream = null;
		}
		logi("DFU service destroyed");
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		// Read input parameters
		final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
		final String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
		final boolean disableNotification = intent.getBooleanExtra(EXTRA_DISABLE_NOTIFICATION, false);
		final boolean foregroundService = intent.getBooleanExtra(EXTRA_FOREGROUND_SERVICE, true);
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

		// Some validation
		if (deviceAddress == null || (filePath == null && fileUri == null && fileResId == 0)) {
			loge("Device Address of firmware location are empty. Hint: use DfuServiceInitiator to start DFU");
			return;
		}
		// Check file type and mime-type
		if ((fileType & ~(TYPE_SOFT_DEVICE | TYPE_BOOTLOADER | TYPE_APPLICATION)) > 0 || !(MIME_TYPE_ZIP.equals(mimeType) || MIME_TYPE_OCTET_STREAM.equals(mimeType))) {
			logw("File type or file mime-type not supported");
			sendLogBroadcast(LOG_LEVEL_WARNING, "File type or file mime-type not supported");
			report(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}
		if (MIME_TYPE_OCTET_STREAM.equals(mimeType) && fileType != TYPE_SOFT_DEVICE && fileType != TYPE_BOOTLOADER && fileType != TYPE_APPLICATION) {
			logw("Unable to determine file type");
			sendLogBroadcast(LOG_LEVEL_WARNING, "Unable to determine file type");
			report(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}
		if (!disableNotification && getNotificationTarget() == null) {
			// This would eventually crash later...
			throw new NullPointerException("getNotificationTarget() must not return null if notifications are enabled");
		}
		if (!foregroundService && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			logw("Foreground service disabled. Android Oreo or newer may kill a background service few moments after user closes the application.\n" +
					"Consider enabling foreground service using DfuServiceInitiator#setForeground(boolean)");
		}
		UuidHelper.assignCustomUuids(intent);

		if (foregroundService) {
			logi("Starting DFU service in foreground");
			startForeground();
		}

		mDeviceAddress = deviceAddress;
		mDeviceName = deviceName;
		mDisableNotification = disableNotification;
		mConnectionState = STATE_DISCONNECTED;
		mError = 0;

		// The Soft Device starts where MBR ends (by default from the address 0x1000).
		// Before there is a MBR section, which should not be transmitted over DFU.
		// Applications and bootloader starts from bigger address. However, in custom DFU
		// implementations, user may want to transmit the whole whole data, even from address 0x0000.
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		int mbrSize = DfuServiceInitiator.DEFAULT_MBR_SIZE;
		if (preferences.contains(DfuSettingsConstants.SETTINGS_MBR_SIZE)) {
			final String value = preferences.getString(DfuSettingsConstants.SETTINGS_MBR_SIZE, String.valueOf(DfuServiceInitiator.DEFAULT_MBR_SIZE));
			try {
				mbrSize = Integer.parseInt(value);
				if (mbrSize < 0)
					mbrSize = 0;
			} catch (final NumberFormatException e) {
				// ignore, default value will be used
			}
		} else {
			mbrSize = intent.getIntExtra(EXTRA_MBR_SIZE, DfuServiceInitiator.DEFAULT_MBR_SIZE);
			if (mbrSize < 0)
				mbrSize = 0;
		}

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "DFU service started");

		/*
		 * First the service is trying to read the firmware and init packet files.
		 */
		InputStream is = mFirmwareInputStream;
		InputStream initIs = mInitFileInputStream;
		try {
			final boolean firstRun = mFirmwareInputStream == null;

			// Prepare data to send, calculate stream size
			try {
				if (firstRun) {
					// The files are opened only once, when DFU service is first started.
					// In case the service needs to be restarted (for example a buttonless service
					// was found or to send Application in the second connection) the input stream
					// is kept as a global service field. This is to avoid SecurityException
					// when the URI was granted with one-time read permission.
					// See: Intent#FLAG_GRANT_READ_URI_PERMISSION (https://developer.android.com/reference/android/content/Intent.html#FLAG_GRANT_READ_URI_PERMISSION).
					sendLogBroadcast(LOG_LEVEL_VERBOSE, "Opening file...");
					if (fileUri != null) {
						is = openInputStream(fileUri, mimeType, mbrSize, fileType);
					} else if (filePath != null) {
						is = openInputStream(filePath, mimeType, mbrSize, fileType);
					} else if (fileResId > 0) {
						is = openInputStream(fileResId, mimeType, mbrSize, fileType);
					}

					// The Init file Input Stream is kept global only in case it was provided
					// as an argument (separate file for HEX/BIN and DAT files).
					// If a ZIP file was given with DAT file(s) inside it will be taken from the ZIP
					// ~20 lines below.
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

					final int imageSizeInBytes = is.available();
					if ((imageSizeInBytes % 4) != 0)
						throw new SizeValidationException("The new firmware is not word-aligned.");
				}

				// Update the file type bit field basing on the ZIP content
				if (MIME_TYPE_ZIP.equals(mimeType)) {
					final ArchiveInputStream zhis = (ArchiveInputStream) is;
					if (fileType == TYPE_AUTO) {
						fileType = zhis.getContentType();
					} else {
						fileType = zhis.setContentType(fileType);
					}

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

				// Mark the beginning of the streams. In case the service is restarted, it should
				// re-upload again the whole file.
				if (firstRun) {
					// The input streams will be reset in initialize(), keep
					is.mark(is.available());
					if (initIs != null)
						initIs.mark(initIs.available());
				}

				mFirmwareInputStream = is;
				mInitFileInputStream = initIs;
				sendLogBroadcast(LOG_LEVEL_INFO, "Firmware file opened successfully");
			} catch (final SecurityException e) {
				loge("A security exception occurred while opening file", e);
				sendLogBroadcast(LOG_LEVEL_ERROR, "Opening file failed: Permission required");
				report(ERROR_FILE_NOT_FOUND);
				return;
			} catch (final FileNotFoundException e) {
				loge("An exception occurred while opening file", e);
				sendLogBroadcast(LOG_LEVEL_ERROR, "Opening file failed: File not found");
				report(ERROR_FILE_NOT_FOUND);
				return;
			} catch (final SizeValidationException e) {
				loge("Firmware not word-aligned", e);
				sendLogBroadcast(LOG_LEVEL_ERROR, "Opening file failed: Firmware size must be word-aligned");
				report(ERROR_FILE_SIZE_INVALID);
				return;
			} catch (final IOException e) {
				loge("An exception occurred while calculating file size", e);
				sendLogBroadcast(LOG_LEVEL_ERROR, "Opening file failed: " + e.getLocalizedMessage());
				report(ERROR_FILE_ERROR);
				return;
			} catch (final Exception e) {
				loge("An exception occurred while opening files. Did you set the firmware file?", e);
				sendLogBroadcast(LOG_LEVEL_ERROR, "Opening file failed: " + e.getLocalizedMessage());
				report(ERROR_FILE_ERROR);
				return;
			}

			if (!firstRun) {
				// Wait a second... If we were connected before it's good to give some time before we start reconnecting.
				waitFor(1000);
				// Looks like a second is not enough. The ACL_DISCONNECTED broadcast sometimes comes later (on Android 7.0)
				waitFor(1000);
			}

			mProgressInfo = new DfuProgressInfo(this);

			if (mAborted) {
				logw("Upload aborted");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
				mProgressInfo.setProgress(PROGRESS_ABORTED);
				return;
			}

			/*
			 * Now let's connect to the device.
			 * All the methods below are synchronous. The mLock object is used to wait for asynchronous calls.
			 */
			sendLogBroadcast(LOG_LEVEL_VERBOSE, "Connecting to DFU target...");
			mProgressInfo.setProgress(PROGRESS_CONNECTING);

			final long before = SystemClock.elapsedRealtime();
			final BluetoothGatt gatt = connect(deviceAddress);
			final long after = SystemClock.elapsedRealtime();
			// Are we connected?
			if (gatt == null) {
				loge("Bluetooth adapter disabled");
				sendLogBroadcast(LOG_LEVEL_ERROR, "Bluetooth adapter disabled");
				report(ERROR_BLUETOOTH_DISABLED);
				return;
			}
			if (mError > 0) { // error occurred
				if ((mError & ERROR_CONNECTION_STATE_MASK) > 0) {
					final int error = mError & ~ERROR_CONNECTION_STATE_MASK;
					logi("Connection error after: " + (after - before) + " ms");
					final boolean timeout = error == 133 && after > before + 25000; // timeout is 30 sec
					if (timeout) {
						loge("Device not reachable. Check if the device with address " + deviceAddress + " is in range, is advertising and is connectable");
						sendLogBroadcast(LOG_LEVEL_ERROR, "Error 133: Connection timeout");
					} else {
						loge("An error occurred while connecting to the device:" + error);
						sendLogBroadcast(LOG_LEVEL_ERROR, String.format(Locale.US, "Connection failed (0x%02X): %s", error, GattError.parseConnectionError(error)));
					}
				} else {
					final int error = mError & ~ERROR_CONNECTION_MASK;
					loge("An error occurred during discovering services:" + error);
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format(Locale.US, "Connection failed (0x%02X): %s", error, GattError.parse(error)));
				}
				// Connection usually fails due to a 133 error (device unreachable, or.. something else went wrong).
				// Usually trying the same for the second time works. Let's try 2 times.
				final int attempt = intent.getIntExtra(EXTRA_RECONNECTION_ATTEMPT, 0);
				logi("Attempt: " + (attempt + 1));
				if (attempt < 2) {
					sendLogBroadcast(LOG_LEVEL_WARNING, "Retrying...");

					if (mConnectionState != STATE_DISCONNECTED) {
						// Disconnect from the device
						disconnect(gatt);
					}
					// Close the device
					refreshDeviceCache(gatt, true);
					close(gatt);

					logi("Restarting the service");
					final Intent newIntent = new Intent();
					newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
					newIntent.putExtra(EXTRA_RECONNECTION_ATTEMPT, attempt + 1);
					startService(newIntent);
					return;
				}
				terminateConnection(gatt, mError);
				return;
			}
			if (mConnectionState == STATE_DISCONNECTED) {
				sendLogBroadcast(LOG_LEVEL_ERROR, "Disconnected");
				terminateConnection(gatt, ERROR_DEVICE_DISCONNECTED);
				return;
			}
			if (mAborted) {
				logw("Upload aborted");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
				terminateConnection(gatt, 0);
				mProgressInfo.setProgress(PROGRESS_ABORTED);
				return;
			}
			sendLogBroadcast(LOG_LEVEL_INFO, "Services discovered");

			// Reset the reconnection attempt counter
			intent.putExtra(EXTRA_RECONNECTION_ATTEMPT, 0);

			DfuService dfuService = null;
			try {
				/*
				 * Device services were discovered. Based on them we may now choose the implementation.
				 */
				final DfuServiceProvider serviceProvider = new DfuServiceProvider();
				mDfuServiceImpl = serviceProvider; // This is required if the provider is now able read data from the device
				mDfuServiceImpl = dfuService = serviceProvider.getServiceImpl(intent, this, gatt);
				if (dfuService == null) {
					Log.w(TAG, "DFU Service not found.");
					sendLogBroadcast(LOG_LEVEL_WARNING, "DFU Service not found");
					terminateConnection(gatt, ERROR_SERVICE_NOT_FOUND);
					return;
				}

				// Begin the DFU depending on the implementation
				if (dfuService.initialize(intent, gatt, fileType, is, initIs)) {
					dfuService.performDfu(intent);
				}
			} catch (final UploadAbortedException e) {
				logw("Upload aborted");
				sendLogBroadcast(LOG_LEVEL_WARNING, "Upload aborted");
				terminateConnection(gatt, 0);
				mProgressInfo.setProgress(PROGRESS_ABORTED);
			} catch (final DeviceDisconnectedException e) {
				sendLogBroadcast(LOG_LEVEL_ERROR, "Device has disconnected");
				loge(e.getMessage());
				close(gatt);

				final int attempt = intent.getIntExtra(EXTRA_DFU_ATTEMPT, 0);
				final int limit = intent.getIntExtra(EXTRA_MAX_DFU_ATTEMPTS, 0);
				if (attempt < limit) {
					logi("Restarting the service (" + (attempt + 1)  + " /" + limit + ")");
					final Intent newIntent = new Intent();
					newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
					newIntent.putExtra(EXTRA_DFU_ATTEMPT, attempt + 1);
					startService(newIntent);
					return;
				}
				report(ERROR_DEVICE_DISCONNECTED);
			} catch (final DfuException e) {
				int error = e.getErrorNumber();
				// Connection state errors and other Bluetooth GATT callbacks share the same error numbers. Therefore we are using bit masks to identify the type.
				if ((error & ERROR_CONNECTION_STATE_MASK) > 0) {
					error &= ~ERROR_CONNECTION_STATE_MASK;
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format(Locale.US, "Error (0x%02X): %s", error, GattError.parseConnectionError(error)));
				} else {
					error &= ~ERROR_CONNECTION_MASK;
					sendLogBroadcast(LOG_LEVEL_ERROR, String.format(Locale.US, "Error (0x%02X): %s", error, GattError.parse(error)));
				}
				loge(e.getMessage());
				terminateConnection(gatt, e.getErrorNumber() /* we return the whole error number, including the error type mask */);
			} finally {
				if (dfuService != null) {
					dfuService.release();
				}
			}
		} finally {
			if (foregroundService) {
				// This will stop foreground state and, if the progress notifications were disabled
				// it will also remove the notification indicating foreground service.
				stopForeground(disableNotification);
			}
		}
	}

	/**
	 * Opens the binary input stream that returns the firmware image content.
	 * A Path to the file is given.
	 *
	 * @param filePath the path to the HEX, BIN or ZIP file.
	 * @param mimeType the file type.
	 * @param mbrSize  the size of MBR, by default 0x1000.
	 * @param types    the content files types in ZIP.
	 * @return The input stream with binary image content.
	 */
	private InputStream openInputStream(@NonNull final String filePath, final String mimeType, final int mbrSize, final int types)
			throws IOException {
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
	 * @param stream   the Uri to the stream.
	 * @param mimeType the file type.
	 * @param mbrSize  the size of MBR, by default 0x1000.
	 * @param types    the content files types in ZIP.
	 * @return The input stream with binary image content.
	 */
	private InputStream openInputStream(@NonNull final Uri stream, final String mimeType, final int mbrSize, final int types)
			throws IOException {
		final InputStream is = getContentResolver().openInputStream(stream);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ArchiveInputStream(is, mbrSize, types);

		final String[] projection = {MediaStore.Images.Media.DISPLAY_NAME};
		final Cursor cursor = getContentResolver().query(stream, projection, null, null, null);
		try {
			if (cursor != null && cursor.moveToNext()) {
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
	 * Opens the binary input stream that returns the firmware image content.
	 * A resource id in the res/raw is given.
	 *
	 * @param resId the if of the resource file.
	 * @param mimeType the file type.
	 * @param mbrSize  the size of MBR, by default 0x1000.
	 * @param types    the content files types in ZIP.
	 * @return The input stream with binary image content.
	 */
	private InputStream openInputStream(final int resId, final String mimeType, final int mbrSize, final int types)
			throws IOException {
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
	 * Connects to the BLE device with given address. This method is SYNCHRONOUS, it wait until
	 * the connection status change from {@link #STATE_CONNECTING} to
	 * {@link #STATE_CONNECTED_AND_READY} or an error occurs.
	 * This method returns <code>null</code> if Bluetooth adapter is disabled.
	 *
	 * @param address the device address.
	 * @return The GATT device or <code>null</code> if Bluetooth adapter is disabled.
	 */
	protected BluetoothGatt connect(@NonNull final String address) {
		if (!mBluetoothAdapter.isEnabled())
			return null;

		mConnectionState = STATE_CONNECTING;

		logi("Connecting to the device...");
		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		BluetoothGatt gatt;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt = device.connectGatt(autoConnect = false, TRANSPORT_LE, preferredPhy = LE_1M | LE_2M)");
			gatt = device.connectGatt(this, false, mGattCallback,
					BluetoothDevice.TRANSPORT_LE,
					BluetoothDevice.PHY_LE_1M_MASK | BluetoothDevice.PHY_LE_2M_MASK);
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt = device.connectGatt(autoConnect = false, TRANSPORT_LE)");
			gatt = device.connectGatt(this, false, mGattCallback,
					BluetoothDevice.TRANSPORT_LE);
		} else {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt = device.connectGatt(autoConnect = false)");
			gatt = device.connectGatt(this, false, mGattCallback);
		}

		// We have to wait until the device is connected and services are discovered
		// Connection error may occur as well.
		try {
			synchronized (mLock) {
				while ((mConnectionState == STATE_CONNECTING || mConnectionState == STATE_CONNECTED) && mError == 0 && !mAborted)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		return gatt;
	}

	/**
	 * Disconnects from the device and cleans local variables in case of error.
	 * This method is SYNCHRONOUS and wait until the disconnecting process will be completed.
	 *
	 * @param gatt  the GATT device to be disconnected.
	 * @param error error number.
	 */
	protected void terminateConnection(@NonNull final BluetoothGatt gatt, final int error) {
		if (mConnectionState != STATE_DISCONNECTED) {
			// Disconnect from the device
			disconnect(gatt);
		}

		// Close the device
		refreshDeviceCache(gatt, false); // This should be set to true when DFU Version is 0.5 or lower
		close(gatt);
		waitFor(600);
		if (error != 0)
			report(error);
	}

	/**
	 * Disconnects from the device. This is SYNCHRONOUS method and waits until the callback returns
	 * new state. Terminates immediately if device is already disconnected. Do not call this method
	 * directly, use {@link #terminateConnection(android.bluetooth.BluetoothGatt, int)} instead.
	 *
	 * @param gatt the GATT device that has to be disconnected.
	 */
	protected void disconnect(@NonNull final BluetoothGatt gatt) {
		if (mConnectionState == STATE_DISCONNECTED)
			return;

		sendLogBroadcast(LOG_LEVEL_VERBOSE, "Disconnecting...");
		mProgressInfo.setProgress(PROGRESS_DISCONNECTING);
		mConnectionState = STATE_DISCONNECTING;

		logi("Disconnecting from the device...");
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.disconnect()");
		gatt.disconnect();

		// We have to wait until device gets disconnected or an error occur
		waitUntilDisconnected();
		sendLogBroadcast(LOG_LEVEL_INFO, "Disconnected");
	}

	/**
	 * Wait until the connection state will change to {@link #STATE_DISCONNECTED} or until
	 * an error occurs.
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
	 * Wait for given number of milliseconds.
	 *
	 * @param millis waiting period.
	 */
	protected void waitFor(final long millis) {
		synchronized (mLock) {
			try {
				sendLogBroadcast(DfuBaseService.LOG_LEVEL_DEBUG, "wait(" + millis + ")");
				mLock.wait(millis);
			} catch (final InterruptedException e) {
				loge("Sleeping interrupted", e);
			}
		}
	}

	/**
	 * Closes the GATT device and cleans up.
	 *
	 * @param gatt the GATT device to be closed.
	 */
	protected void close(@NonNull final BluetoothGatt gatt) {
		logi("Cleaning up...");
		// Call disconnect() to make sure all resources are released. The device should already be
		// disconnected, but that's OK.
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.disconnect()");
		gatt.disconnect();
		sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.close()");
		gatt.close();
		mConnectionState = STATE_CLOSED;
	}

	/**
	 * Clears the device cache. After uploading new firmware the DFU target will have other
	 * services than before.
	 *
	 * @param gatt  the GATT device to be refreshed.
	 * @param force <code>true</code> to force the refresh.
	 */
	protected void refreshDeviceCache(@NonNull final BluetoothGatt gatt, final boolean force) {
		/*
		 * If the device is bonded this is up to the Service Changed characteristic to notify Android
		 * that the services has changed. There is no need for this trick in that case.
		 * If not bonded, the Android should not keep the services cached when the Service Changed
		 * characteristic is present in the target device database.
		 * However, due to the Android bug, it is keeping them anyway and the only way to clear
		 * services is by using this hidden refresh method.
		 */
		if (force || gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
			sendLogBroadcast(LOG_LEVEL_DEBUG, "gatt.refresh() (hidden)");
			/*
			 * There is a refresh() method in BluetoothGatt class but for now it's hidden.
			 * We will call it using reflections.
			 */
			try {
				//noinspection JavaReflectionMemberAccess
				final Method refresh = gatt.getClass().getMethod("refresh");
				//noinspection ConstantConditions
				final boolean success = (Boolean) refresh.invoke(gatt);
				logi("Refreshing result: " + success);
			} catch (final Exception e) {
				loge("An exception occurred while refreshing device", e);
				sendLogBroadcast(LOG_LEVEL_WARNING, "Refreshing failed");
			}
		}
	}

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with
	 * given progress state to the activity.
	 */
	@Override
	public void updateProgressNotification() {
		final DfuProgressInfo info = mProgressInfo;
		final int progress = info.getProgress();
		if (mLastProgress == progress)
			return;

		mLastProgress = progress;

		// send progress or error broadcast
		sendProgressBroadcast(info);

		if (mDisableNotification)
			return;

		// the notification may not be refreshed too quickly as the ABORT button becomes not clickable
		// If new state is an end-state, update regardless so it will not stick around in "Disconnecting" state
		final long now = SystemClock.elapsedRealtime();
		if (now - mLastNotificationTime < 250 && !(PROGRESS_COMPLETED == progress || PROGRESS_ABORTED == progress))
			return;
		mLastNotificationTime = now;

		// create or update notification:
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_DFU)
				.setSmallIcon(android.R.drawable.stat_sys_upload).setOnlyAlertOnce(true);//.setLargeIcon(largeIcon);
		// Android 5
		builder.setColor(Color.GRAY);

		switch (progress) {
			case PROGRESS_CONNECTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_connecting)).setContentText(getString(R.string.dfu_status_connecting_msg, deviceName))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_STARTING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_starting)).setContentText(getString(R.string.dfu_status_starting_msg))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_ENABLING_DFU_MODE:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_switching_to_dfu)).setContentText(getString(R.string.dfu_status_switching_to_dfu_msg))
						.setProgress(100, 0, true);
				break;
			case PROGRESS_VALIDATING:
				builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_validating)).setContentText(getString(R.string.dfu_status_validating_msg))
						.setProgress(100, 0, true);
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
				builder.setOngoing(true).setContentTitle(title).setContentText(text)
						.setProgress(100, progress, false);
				break;
		}

		// update the notification
		final Intent intent = new Intent(this, getNotificationTarget());
		intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		intent.putExtra(EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(EXTRA_PROGRESS, progress);
		final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
		builder.setContentIntent(pendingIntent);

		// Any additional configuration?
		updateProgressNotification(builder, progress);

		final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, builder.build());
		}
	}

	/**
	 * This method allows you to update the notification showing the upload progress.
	 *
	 * @param builder notification builder.
	 */
	protected void updateProgressNotification(@NonNull final NotificationCompat.Builder builder, final int progress) {
		// Add Abort action to the notification
		if (progress != PROGRESS_ABORTED && progress != PROGRESS_COMPLETED) {
			final Intent abortIntent = new Intent(BROADCAST_ACTION);
			abortIntent.putExtra(EXTRA_ACTION, ACTION_ABORT);
			final PendingIntent pendingAbortIntent = PendingIntent.getBroadcast(this, 1, abortIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.addAction(R.drawable.ic_action_notify_cancel, getString(R.string.dfu_action_abort), pendingAbortIntent);
		}
	}

	/**
	 * Creates or updates the notification in the Notification Manager. Sends broadcast with given
	 * error number to the activity.
	 *
	 * @param error the error number.
	 */
	private void report(final int error) {
		sendErrorBroadcast(error);

		if (mDisableNotification)
			return;

		// create or update notification:
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_DFU)
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

		// Any additional configuration?
		updateErrorNotification(builder);

		final NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (manager != null) {
			manager.notify(NOTIFICATION_ID, builder.build());
		}
	}

	/**
	 * This method allows you to update the notification showing an error.
	 * @param builder error notification builder
	 */
	@SuppressWarnings("unused")
	protected void updateErrorNotification(@NonNull final NotificationCompat.Builder builder) {
		// Empty default implementation
	}

	private void startForeground() {
		final NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_DFU)
				.setSmallIcon(android.R.drawable.stat_sys_upload)
				.setContentTitle(getString(R.string.dfu_status_foreground_title)).setContentText(getString(R.string.dfu_status_foreground_content))
				.setColor(Color.GRAY)
				.setPriority(NotificationCompat.PRIORITY_LOW)
				.setOngoing(true);

		// Update the notification
		final Class<? extends Activity> clazz = getNotificationTarget();
		if (clazz != null) {
			final Intent targetIntent = new Intent(this, clazz);
			targetIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			targetIntent.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
			targetIntent.putExtra(EXTRA_DEVICE_NAME, mDeviceName);
			final PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, targetIntent, PendingIntent.FLAG_UPDATE_CURRENT);
			builder.setContentIntent(pendingIntent);
		} else {
			logw("getNotificationTarget() should not return null if the service is to be started as a foreground service");
			// otherwise the notification will not be clickable.
		}

		// Any additional configuration?
		updateForegroundNotification(builder);

		startForeground(NOTIFICATION_ID, builder.build());
	}

	/**
	 * This method allows you to update the notification that will be shown when the service goes to
	 * the foreground state.
	 *
	 * @param builder foreground notification builder
	 */
	@SuppressWarnings("unused")
	protected void updateForegroundNotification(@NonNull final NotificationCompat.Builder builder) {
		// Empty default implementation
	}

	/**
	 * This method must return the activity class that will be used to create the pending intent
	 * used as a content intent in the notification showing the upload progress
	 * or service foreground state. The activity will be launched when user click the notification.
	 * DfuService will add {@link android.content.Intent#FLAG_ACTIVITY_NEW_TASK} flag and the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - target device address</li>
	 * <li>{@link #EXTRA_DEVICE_NAME} - target device name</li>
	 * <li>{@link #EXTRA_PROGRESS} - the connection state (values &lt; 0)*, current progress (0-100)
	 * 		or error number if {@link #ERROR_MASK} bit set.</li>
	 * </ul>
	 * <p>
	 * The {@link #EXTRA_PROGRESS} is not set when a notification indicating a foreground service
	 * was clicked and notifications were disabled using {@link DfuServiceInitiator#setDisableNotification(boolean)}.
	 * <p>
	 * If your application disabled DFU notifications by calling
	 * {@link DfuServiceInitiator#setDisableNotification(boolean)} with parameter <code>true</code> this method
	 * will still be called if the service was started as foreground service. To disable foreground service
	 * call {@link DfuServiceInitiator#setForeground(boolean)} with parameter <code>false</code>.
	 * _______________________________<br>
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
	 *
	 * @return The target activity class.
	 */
	@Nullable
	protected abstract Class<? extends Activity> getNotificationTarget();

	/**
	 * Override this method to enable detailed debug LogCat logs with DFU events.
	 * <p>Recommended use:</p>
	 * <pre>
	 * &#64;Override
	 * protected boolean isDebug() {
	 *     return BuildConfig.DEBUG;
	 * }
	 * </pre>
	 * @return True to enable LogCat output, false (default) if not.
	 */
	protected boolean isDebug() {
		// Override this method and return true if you need more logs in LogCat
		// Note: BuildConfig.DEBUG always returns false in library projects, so please use
		// your app package BuildConfig
		return false;
	}

	private void sendProgressBroadcast(final DfuProgressInfo info) {
		final Intent broadcast = new Intent(BROADCAST_PROGRESS);
		broadcast.putExtra(EXTRA_DATA, info.getProgress());
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		broadcast.putExtra(EXTRA_PART_CURRENT, info.getCurrentPart());
		broadcast.putExtra(EXTRA_PARTS_TOTAL, info.getTotalParts());
		broadcast.putExtra(EXTRA_SPEED_B_PER_MS, info.getSpeed());
		broadcast.putExtra(EXTRA_AVG_SPEED_B_PER_MS, info.getAverageSpeed());
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
			broadcast.putExtra(EXTRA_DATA, error & ~ERROR_REMOTE_MASK);
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
	 * Initializes bluetooth adapter.
	 *
	 * @return <code>True</code> if initialization was successful.
	 */
	@SuppressWarnings("UnusedReturnValue")
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
		if (DfuBaseService.DEBUG)
			Log.w(TAG, message);
	}

	private void logi(final String message) {
		if (DfuBaseService.DEBUG)
			Log.i(TAG, message);
	}
}
