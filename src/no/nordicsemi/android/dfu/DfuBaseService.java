/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. 
 * This heading must NOT be removed from the file.
 ******************************************************************************/
package no.nordicsemi.android.dfu;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

import no.nordicsemi.android.dfu.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.exception.DfuException;
import no.nordicsemi.android.dfu.exception.HexFileValidationException;
import no.nordicsemi.android.dfu.exception.RemoteDfuException;
import no.nordicsemi.android.dfu.exception.UnknownResponseException;
import no.nordicsemi.android.dfu.exception.UploadAbortedException;
import no.nordicsemi.android.error.GattError;
import no.nordicsemi.android.log.ILogSession;
import no.nordicsemi.android.log.LogContract;
import no.nordicsemi.android.log.LogContract.Log.Level;
import no.nordicsemi.android.log.Logger;
import android.app.Activity;
import android.app.IntentService;
import android.app.Notification;
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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

/**
 * The DFU Service provides full support for Over-the-Air (OTA) Device Firmware Update by Nordic Semiconductor.<br />
 * With the Soft Device 7.0.0+ it allows to upload a new Soft Device, new Bootloader and a new Application. For odler soft devices only Application update is supported.
 * <p>
 * To run the service to your application inherit it and overwrite the missing method. Remember to it to the AndroidManifest.xml file. Start the service with the following parameters:
 * 
 * <pre>
 * final Intent service = new Intent(this, YourDfuService.class);
 * service.putExtra(DfuService.EXTRA_DEVICE_ADDRESS, mSelectedDevice.getAddress());
 * service.putExtra(DfuService.EXTRA_DEVICE_NAME, mSelectedDevice.getName());
 * service.putExtra(DfuService.EXTRA_FILE_MIME_TYPE, mFileType == DfuService.TYPE_AUTO ? YourDfuService.MIME_TYPE_ZIP : YourDfuService.MIME_TYPE_HEX);
 * service.putExtra(DfuService.EXTRA_FILE_TYPE, mFileType);
 * service.putExtra(DfuService.EXTRA_FILE_PATH, mFilePath);
 * service.putExtra(DfuService.EXTRA_FILE_URI, mFileStreamUri);
 * startService(service);
 * </pre>
 * 
 * The {@link #EXTRA_FILE_MIME_TYPE} and {@link #EXTRA_FILE_TYPE} parameters are optional. If not provided the application upload from HEX file is assumed. The service API is compatible with previous
 * versions.
 * </p>
 * <p>
 * The service will show its progress in the notifications bar and will send local broadcasts the the application.
 * </p>
 */
public abstract class DfuBaseService extends IntentService {
	private static final String TAG = "DfuService";
	final private static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

	/** A key for {@link SharedPreferences} entry that keeps information whether the upload is currently in progress. This may be used to get this information during activity creation. */
	public static final String DFU_IN_PROGRESS = "no.nordicsemi.android.dfu.PREFS_DFU_IN_PROGRESS";

	public static final String EXTRA_DEVICE_ADDRESS = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_ADDRESS";
	public static final String EXTRA_DEVICE_NAME = "no.nordicsemi.android.dfu.extra.EXTRA_DEVICE_NAME";
	public static final String EXTRA_LOG_URI = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_URI";
	public static final String EXTRA_FILE_PATH = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_PATH";
	public static final String EXTRA_FILE_URI = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_URI";

	/**
	 * The input file mime-type. Currently only "application/zip" (ZIP) or "application/octet-stream" (HEX) are supported. If this parameter is empty the "application/octet-stream" is assumed.
	 */
	public static final String EXTRA_FILE_MIME_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_MIME_TYPE";

	/**
	 * This optional extra parameter may contain a file type. Currently supported are:
	 * <ul>
	 * <li>{@link #TYPE_SOFT_DEVICE} - only Soft Device update</li>
	 * <li>{@link #TYPE_BOOTLOADER} - only Bootloader update</li>
	 * <li>{@link #TYPE_APPLICATION} - only application update</li>
	 * <li>{@link #TYPE_AUTO} -the file is a ZIP file that may contain more than one HEX. The ZIP file MAY contain only the following files: <b>softdevice.hex</b>, <b>bootloader.hex</b>,
	 * <b>application.hex</b> to determine the type based on its name. At lease one of them MUST be present.</li>
	 * </ul>
	 * If this parameter is not provided the type is assumed as follows:
	 * <ol>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is <code>null</code> or is equal to {@value #MIME_TYPE_HEX} - the {@link #TYPE_APPLICATION} is assumed.</li>
	 * <li>If the {@link #EXTRA_FILE_MIME_TYPE} field is equal to {@value #MIME_TYPE_ZIP} - the {@link #TYPE_AUTO} is assumed.</li>
	 * </ol>
	 */
	public static final String EXTRA_FILE_TYPE = "no.nordicsemi.android.dfu.extra.EXTRA_FILE_TYPE";
	/**
	 * The file contains a new version of Soft Device.
	 * 
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_SOFT_DEVICE = 0x01;
	/**
	 * The file contains a new version of Bootloader.
	 * 
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_BOOTLOADER = 0x02;
	/**
	 * The file contains a new version of Application.
	 * 
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_APPLICATION = 0x04;
	/**
	 * A ZIP file that combines more than 1 HEX file. The names of files in the ZIP must be: <b>softdevice.hex</b>, <b>bootloader.hex</b>, <b>application.hex</b> in order to be read correctly. In the
	 * DFU version 2 the Soft Device and Bootloader may be sent together. In case of additional application file included, the service will try to send Soft Device, Bootloader and Application together
	 * (not supported by DFU v.2) and if it fails, send first SD+BL, reconnect and send application.
	 * 
	 * @see #EXTRA_FILE_TYPE
	 */
	public static final int TYPE_AUTO = 0x00;

	/** Extra to send progress and error broadcast events. */
	public static final String EXTRA_DATA = "no.nordicsemi.android.dfu.extra.EXTRA_DATA";
	/**
	 * Extra to send progress or error information in the DFU notification. The value may contain:
	 * <ul>
	 * <li>Value 0 - 100 - percentage progress value</li>
	 * <li>One of the following status constants:
	 * <ul>
	 * <li>{@link #PROGRESS_CONNECTING}</li>
	 * <li>{@link #PROGRESS_STARTING}</li>
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * <li>{@link #PROGRESS_DISCONNECTING}</li>
	 * <li>{@link #PROGRESS_COMPLETED}</li>
	 * <li>{@link #PROGRESS_ABORTED}</li>
	 * </ul>
	 * </li>
	 * <li>An error code with {@link #ERROR_MASK} if initialization error occurred</li>
	 * <li>An error code with {@link #ERROR_REMOTE_MASK} if remote DFU target returned an error</li>
	 * <li>An error code with {@link #ERROR_CONNECTION_MASK} if connection error occurred (f.e. Gatt error (133) or Internal Gatt Error (129))</li>
	 * </ul>
	 * To check if error occurred use:<br />
	 * {@code boolean error = progressValue >= DfuBaseService.ERROR_MASK;}
	 */
	public static final String EXTRA_PROGRESS = "no.nordicsemi.android.dfu.extra.EXTRA_PROGRESS";
	/**
	 * The number of currently transferred part. The SoftDevice and Bootloader may be send together as one part. If user wants to upload them together with an application it has to be sent
	 * in another connection as the second part.
	 * 
	 * @see DfuBaseService#EXTRA_PARTS_TOTAL
	 */
	public static final String EXTRA_PART_CURRENT = "no.nordicsemi.android.dfu.extra.EXTRA_PART_CURRENT";
	/**
	 * Number of parts in total.
	 * 
	 * @see DfuBaseService#EXTRA_PART_CURRENT
	 */
	public static final String EXTRA_PARTS_TOTAL = "no.nordicsemi.android.dfu.extra.EXTRA_PARTS_TOTAL";
	/** The current upload speed in bytes/millisecond. */
	public static final String EXTRA_SPEED_B_PER_MS = "no.nordicsemi.android.dfu.extra.EXTRA_SPEED_B_PER_MS";
	/** The average upload speed in bytes/millisecond for the current part. */
	public static final String EXTRA_AVG_SPEED_B_PER_MS = "no.nordicsemi.android.dfu.extra.EXTRA_AVG_SPEED_B_PER_MS";

	/**
	 * The broadcast message contains the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DATA} - the progress value (percentage 0-100) or:
	 * <ul>
	 * <li>{@link #PROGRESS_CONNECTING}</li>
	 * <li>{@link #PROGRESS_STARTING}</li>
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * <li>{@link #PROGRESS_DISCONNECTING}</li>
	 * <li>{@link #PROGRESS_COMPLETED}</li>
	 * <li>{@link #PROGRESS_ABORTED}</li>
	 * </ul>
	 * </li>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - the target device address</li>
	 * <li>{@link #EXTRA_PART_CURRENT} - the number of currently transmitted part</li>
	 * <li>{@link #EXTRA_PARTS_TOTAL} - number number of parts to send, f.e. a SoftDevice and Bootloader may be send together as one part, then application as the second part.</li>
	 * </ul>
	 */
	public static final String BROADCAST_PROGRESS = "no.nordicsemi.android.dfu.broadcast.BROADCAST_PROGRESS";
	/** Service is connecting to the remote DFU target. */
	public static final int PROGRESS_CONNECTING = -1;
	/** Service is enabling notifications and starting transmission. */
	public static final int PROGRESS_STARTING = -2;
	/** Service is sending validation request to the remote DFU target. */
	public static final int PROGRESS_VALIDATING = -4;
	/** Service is disconnecting from the DFU target. */
	public static final int PROGRESS_DISCONNECTING = -5;
	/** The connection is successful. */
	public static final int PROGRESS_COMPLETED = -6;
	/** The upload has been aborted. Previous software version will be restored on the target. */
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
	 * If this bit is set than the progress value indicates an error. Use {@link GattError#parse(int)} to obtain error name.
	 */
	public static final int ERROR_MASK = 0x0100;
	public static final int ERROR_DEVICE_DISCONNECTED = ERROR_MASK | 0x00;
	public static final int ERROR_FILE_NOT_FOUND = ERROR_MASK | 0x01;
	/** Thrown if service was unable to open the file ({@link IOException} has been thrown). */
	public static final int ERROR_FILE_ERROR = ERROR_MASK | 0x02;
	/** Thrown then input file is not a valid HEX or ZIP file. */
	public static final int ERROR_FILE_INVALID = ERROR_MASK | 0x03;
	/** Thrown when {@link IOException} occurred when reading from file. */
	public static final int ERROR_FILE_IO_EXCEPTION = ERROR_MASK | 0x04;
	/** Error thrown then {@code gatt.discoverServices();} returns false. */
	public static final int ERROR_SERVICE_DISCOVERY_NOT_STARTED = ERROR_MASK | 0x05;
	/** Thrown when the service discovery has finished but the DFU service has not been found. The device does not support DFU of is not in DFU mode. */
	public static final int ERROR_SERVICE_NOT_FOUND = ERROR_MASK | 0x06;
	/** Thrown when the required DFU service has been found but at least one of the DFU characteristics is absent. */
	public static final int ERROR_CHARACTERISTICS_NOT_FOUND = ERROR_MASK | 0x07;
	/** Thrown when unknown response has been obtained from the target. The DFU target must follow specification. */
	public static final int ERROR_INVALID_RESPONSE = ERROR_MASK | 0x08;
	/** Thrown when the the service does not support given type or mime-type. */
	public static final int ERROR_FILE_TYPE_UNSUPPORTED = ERROR_MASK | 0x09;
	/** Flag set then the DFU target returned a DFU error. Look for DFU specification to get error codes. */
	public static final int ERROR_REMOTE_MASK = 0x0200;
	/** The flag set when one of {@link BluetoothGattCallback} methods was called with status other than {@link BluetoothGatt#GATT_SUCCESS}. */
	public static final int ERROR_CONNECTION_MASK = 0x0400;

	/**
	 * The log events are only broadcasted when there is no nRF Logger installed. The broadcast contains 2 extras:
	 * <ul>
	 * <li>{@link #EXTRA_LOG_LEVEL} - The log level: one of {@link LogContract.Log.Level#DEBUG}, {@link LogContract.Log.Level#VERBOSE}, {@link LogContract.Log.Level#INFO},
	 * {@link LogContract.Log.Level#WARNING}, {@link LogContract.Log.Level#ERROR}</li>
	 * <li>{@link #EXTRA_LOG_MESSAGE}</li>
	 * </ul>
	 */
	public static final String BROADCAST_LOG = "no.nordicsemi.android.dfu.broadcast.BROADCAST_LOG";
	public static final String EXTRA_LOG_MESSAGE = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_INFO";
	public static final String EXTRA_LOG_LEVEL = "no.nordicsemi.android.dfu.extra.EXTRA_LOG_LEVEL";

	/** Activity may broadcast this broadcast in order to pause, resume or abort DFU process. */
	public static final String BROADCAST_ACTION = "no.nordicsemi.android.dfu.broadcast.BROADCAST_ACTION";
	public static final String EXTRA_ACTION = "no.nordicsemi.android.dfu.extra.EXTRA_ACTION";
	public static final int ACTION_PAUSE = 0;
	public static final int ACTION_RESUME = 1;
	public static final int ACTION_ABORT = 2;

	public static final int NOTIFICATION_ID = 283; // a random number

	private BluetoothAdapter mBluetoothAdapter;
	private String mDeviceAddress;
	private String mDeviceName;
	private ILogSession mLogSession;
	/** Lock used in synchronization purposes */
	private final Object mLock = new Object();

	/** The number of the last error that has occurred or 0 if there was no error */
	private int mErrorState;
	/** The current connection state. If its value is > 0 than an error has occurred. Error number is a negative value of mConnectionState */
	private int mConnectionState;
	private final static int STATE_DISCONNECTED = 0;
	private final static int STATE_CONNECTING = -1;
	private final static int STATE_CONNECTED = -2;
	private final static int STATE_CONNECTED_AND_READY = -3; // indicates that services were discovered
	private final static int STATE_DISCONNECTING = -4;
	private final static int STATE_CLOSED = -5;

	/** Flag set when we got confirmation from the device that notifications are enabled. */
	private boolean mNotificationsEnabled;

	private final static int MAX_PACKET_SIZE = 20; // the maximum number of bytes in one packet is 20. May be less.
	/** The number of packets of firmware data to be send before receiving a new Packets receipt notification. 0 disables the packets notifications */
	private int mPacketsBeforeNotification = 10;

	private byte[] mBuffer = new byte[MAX_PACKET_SIZE];
	private InputStream mInputStream;
	/** Size of BIN content of all hex files that are going to be transmitted. */
	private int mImageSizeInBytes;
	/** Number of bytes transmitted. */
	private int mBytesSent;
	/** Number of bytes confirmed by the notification. */
	@SuppressWarnings("unused")
	private int mBytesConfirmed;
	private int mPacketsSentSinceNotification;
	/** This value is used to calculate the current transfer speed. */
	private int mLastBytesSent;
	/**
	 * Firmware update may require two connections: one for Soft Device and/or Bootloader upload and second for Application. This fields contains the current part number.
	 */
	private int mPartCurrent;
	/** Total number of parts. */
	private int mPartsTotal;
	private int mFileType;
	private boolean mPaused;
	private boolean mAborted;
	private boolean mResetRequestSent;
	private long mLastProgressTime, mStartTime;

	/** Flag indicating whether the image size has been already transfered or not */
	private boolean mImageSizeSent;
	/** Flag indicating whether the request was completed or not */
	private boolean mRequestCompleted;

	/** Latest data received from device using notification. */
	private byte[] mReceivedData = null;
	private static final int OP_CODE_RECEIVE_START_DFU_KEY = 0x01; // 1
	private static final int OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY = 0x03; // 3
	private static final int OP_CODE_RECEIVE_VALIDATE_KEY = 0x04; // 4
	private static final int OP_CODE_RECEIVE_ACTIVATE_AND_RESET_KEY = 0x05; // 5
	private static final int OP_CODE_RECEIVE_RESET_KEY = 0x06; // 6
	//	private static final int OP_CODE_PACKET_REPORT_RECEIVED_IMAGE_SIZE_KEY = 0x07; // 7
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY = 0x08; // 8
	private static final int OP_CODE_RESPONSE_CODE_KEY = 0x10; // 16
	private static final int OP_CODE_PACKET_RECEIPT_NOTIF_KEY = 0x11; // 11
	private static final byte[] OP_CODE_START_DFU = new byte[] { OP_CODE_RECEIVE_START_DFU_KEY, 0x00 };
	private static final byte[] OP_CODE_RECEIVE_FIRMWARE_IMAGE = new byte[] { OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY };
	private static final byte[] OP_CODE_VALIDATE = new byte[] { OP_CODE_RECEIVE_VALIDATE_KEY };
	private static final byte[] OP_CODE_ACTIVATE_AND_RESET = new byte[] { OP_CODE_RECEIVE_ACTIVATE_AND_RESET_KEY };
	private static final byte[] OP_CODE_RESET = new byte[] { OP_CODE_RECEIVE_RESET_KEY };
	//	private static final byte[] OP_CODE_REPORT_RECEIVED_IMAGE_SIZE = new byte[] { OP_CODE_PACKET_REPORT_RECEIVED_IMAGE_SIZE_KEY };
	private static final byte[] OP_CODE_PACKET_RECEIPT_NOTIF_REQ = new byte[] { OP_CODE_PACKET_RECEIPT_NOTIF_REQ_KEY, 0x00, 0x00 };

	public static final int DFU_STATUS_SUCCESS = 1;
	public static final int DFU_STATUS_INVALID_STATE = 2;
	public static final int DFU_STATUS_NOT_SUPPORTED = 3;
	public static final int DFU_STATUS_DATA_SIZE_EXCEEDS_LIMIT = 4;
	public static final int DFU_STATUS_CRC_ERROR = 5;
	public static final int DFU_STATUS_OPERATION_FAILED = 6;

	public static final UUID DFU_SERVICE_UUID = new UUID(0x000015301212EFDEl, 0x1523785FEABCD123l);
	private static final UUID DFU_CONTROL_POINT_UUID = new UUID(0x000015311212EFDEl, 0x1523785FEABCD123l);
	private static final UUID DFU_PACKET_UUID = new UUID(0x000015321212EFDEl, 0x1523785FEABCD123l);
	private static final UUID CLIENT_CHARACTERISTIC_CONFIG = new UUID(0x0000290200001000l, 0x800000805f9b34fbl);

	public static final String MIME_TYPE_HEX = "application/octet-stream";
	public static final String MIME_TYPE_ZIP = "application/zip";

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

				// notify waiting thread
				synchronized (mLock) {
					mLock.notifyAll();
				}
				break;
			case ACTION_ABORT:
				mPaused = false;
				mAborted = true;

				// notify waiting thread
				synchronized (mLock) {
					mLock.notifyAll();
				}
				break;
			}
		}
	};

	private final BroadcastReceiver mConnectionStateBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(final Context context, final Intent intent) {
			// obtain the device and check it this is the one that we are connected to 
			final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (!device.getAddress().equals(mDeviceAddress))
				return;

			final String action = intent.getAction();

			logi("Action received: " + action);
			mConnectionState = STATE_DISCONNECTED;
		}
	};

	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			// check whether an error occurred 
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					logi("Connected to GATT server");
					mConnectionState = STATE_CONNECTED;

					// Attempts to discover services after successful connection.
					// do not refresh the gatt device here!
					final boolean success = gatt.discoverServices();
					logi("Attempting to start service discovery... " + (success ? "succeed" : "failed"));

					if (!success) {
						mErrorState = ERROR_SERVICE_DISCOVERY_NOT_STARTED;
					} else {
						// just return here, lock will be notified when service discovery finishes
						return;
					}
				} else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					logi("Disconnected from GATT server");
					mPaused = false;
					mConnectionState = STATE_DISCONNECTED;
				}
			} else {
				loge("Connection state change error: " + status + " newState: " + newState);
				mErrorState = ERROR_CONNECTION_MASK | status;
			}

			// notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
				return;
			}
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				logi("Services discovered");
				mConnectionState = STATE_CONNECTED_AND_READY;
			} else {
				loge("Service discovery error: " + status);
				mErrorState = ERROR_CONNECTION_MASK | status;
			}

			// notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
				return;
			}
		}

		@Override
		public void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (CLIENT_CHARACTERISTIC_CONFIG.equals(descriptor.getUuid())) {
					// we have enabled or disabled characteristic
					mNotificationsEnabled = descriptor.getValue()[0] == 1;
				}
			} else {
				loge("Descriptor write error: " + status);
				mErrorState = ERROR_CONNECTION_MASK | status;
			}

			// notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
				return;
			}
		};

		@Override
		public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				/*
				 * This method is called when either a CONTROL POINT or PACKET characteristic has been written.
				 * If it is the CONTROL POINT characteristic, just set the flag to true.
				 * If the PACKET characteristic was written we must:
				 * - if the image size was written in DFU Start procedure, just set flag to true
				 * - else
				 * - send the next packet, if notification is not required at that moment
				 * - do nothing, because we have to wait for the notification to confirm the data received
				 */
				if (DFU_PACKET_UUID.equals(characteristic.getUuid())) {
					if (mImageSizeSent) {
						// if the PACKET characteristic was written with image data, update counters
						mBytesSent += characteristic.getValue().length;
						mPacketsSentSinceNotification++;

						// if a packet receipt notification is expected, or the last packet was sent, do nothing. There onCharacteristicChanged listener will catch either 
						// a packet confirmation (if there are more bytes to send) or the image received notification (it upload process was completed)
						final boolean notificationExpected = mPacketsBeforeNotification > 0 && mPacketsSentSinceNotification == mPacketsBeforeNotification;
						final boolean lastPacketTransfered = mBytesSent == mImageSizeInBytes;

						if (notificationExpected || lastPacketTransfered)
							return;

						// when neither of them is true, send the next packet
						try {
							waitIfPaused();
							if (mAborted) {
								// notify waiting thread
								synchronized (mLock) {
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
							mErrorState = ERROR_FILE_INVALID;
						} catch (final IOException e) {
							loge("Error while reading the input stream", e);
							mErrorState = ERROR_FILE_IO_EXCEPTION;
						}
					} else {
						// we've got confirmation that the image size was sent
						mImageSizeSent = true;
					}
				} else {
					// if the CONTROL POINT characteristic was written just set the flag to true
					sendLogBroadcast(Level.INFO, "Data written to " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
					mRequestCompleted = true;
				}
			} else {
				/*
				 * If a Reset (Op Code = 6) or Activate and Reset (Op Code = 5) commands are sent the DFU target resets and sometimes does it so quickly that does not manage to send
				 * any ACK to the controller and error 133 is thrown here.
				 */
				if (mResetRequestSent)
					mRequestCompleted = true;
				else {
					loge("Characteristic write error: " + status);
					mErrorState = ERROR_CONNECTION_MASK | status;
				}
			}

			// notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
				return;
			}
		};

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
					if (mAborted)
						break;

					final byte[] buffer = mBuffer;
					final int size = mInputStream.read(buffer);
					writePacket(gatt, packetCharacteristic, buffer, size);
					updateProgressNotification();
					return;
				} catch (final HexFileValidationException e) {
					loge("Invalid HEX file");
					mErrorState = ERROR_FILE_INVALID;
				} catch (final IOException e) {
					loge("Error while reading the input stream", e);
					mErrorState = ERROR_FILE_IO_EXCEPTION;
				}
				break;
			default:
				sendLogBroadcast(Level.INFO, "Received Read Response from " + characteristic.getUuid() + ", value (0x): " + parse(characteristic));
				mReceivedData = characteristic.getValue();
				break;
			}

			// notify waiting thread
			synchronized (mLock) {
				mLock.notifyAll();
				return;
			}
		};

		public String parse(final BluetoothGattCharacteristic characteristic) {
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

	public DfuBaseService() {
		super(TAG);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		initialize();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		final IntentFilter actionFilter = makeDfuActionIntentFilter();
		manager.registerReceiver(mDfuActionReceiver, actionFilter);
		// We must register this as a non-local receiver to get broadcasts from the notification action
		registerReceiver(mDfuActionReceiver, actionFilter);

		final IntentFilter filter = new IntentFilter();
		filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
		registerReceiver(mConnectionStateBroadcastReceiver, filter);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		final LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
		manager.unregisterReceiver(mDfuActionReceiver);

		unregisterReceiver(mDfuActionReceiver);
		unregisterReceiver(mConnectionStateBroadcastReceiver);
	}

	@Override
	protected void onHandleIntent(final Intent intent) {
		final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		// In order to let DfuActivity know whether DFU is in progress, we have to use Shared Preferences 
		final SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean(DFU_IN_PROGRESS, true);
		editor.commit();

		// Read input parameters
		final String deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS);
		final String deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME);
		final String filePath = intent.getStringExtra(EXTRA_FILE_PATH);
		final Uri fileUri = intent.getParcelableExtra(EXTRA_FILE_URI);
		final Uri logUri = intent.getParcelableExtra(EXTRA_LOG_URI);
		int fileType = intent.getIntExtra(EXTRA_FILE_TYPE, TYPE_AUTO);
		if (filePath != null && fileType == TYPE_AUTO)
			fileType = filePath.toLowerCase(Locale.US).endsWith("zip") ? TYPE_AUTO : TYPE_APPLICATION;
		String mimeType = intent.getStringExtra(EXTRA_FILE_MIME_TYPE);
		mimeType = mimeType != null ? mimeType : (fileType == TYPE_AUTO ? MIME_TYPE_ZIP : MIME_TYPE_HEX);
		mLogSession = Logger.openSession(this, logUri);
		mPartCurrent = intent.getIntExtra(EXTRA_PART_CURRENT, 1);
		mPartsTotal = intent.getIntExtra(EXTRA_PARTS_TOTAL, 1);

		// Check file type and mime-type
		if ((fileType & ~(TYPE_SOFT_DEVICE | TYPE_BOOTLOADER | TYPE_APPLICATION)) > 0 || !(MIME_TYPE_ZIP.equals(mimeType) || MIME_TYPE_HEX.equals(mimeType))) {
			logw("File type or file mime-type not supported");
			sendLogBroadcast(Level.WARNING, "File type or file mime-type not supported");
			sendErrorBroadcast(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}
		if (MIME_TYPE_HEX.equals(mimeType) && fileType != TYPE_SOFT_DEVICE && fileType != TYPE_BOOTLOADER && fileType != TYPE_APPLICATION) {
			logw("Unable to determine file type");
			sendLogBroadcast(Level.WARNING, "Unable to determine file type");
			sendErrorBroadcast(ERROR_FILE_TYPE_UNSUPPORTED);
			return;
		}

		mDeviceAddress = deviceAddress;
		mDeviceName = deviceName;
		mConnectionState = STATE_DISCONNECTED;
		mBytesSent = 0;
		mBytesConfirmed = 0;
		mPacketsSentSinceNotification = 0;
		mErrorState = 0;
		mAborted = false;
		mPaused = false;
		mNotificationsEnabled = false;
		mResetRequestSent = false;
		mRequestCompleted = false;
		mImageSizeSent = false;

		// read preferences
		final boolean packetReceiptNotificationEnabled = preferences.getBoolean(DfuSettingsConstants.SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED, true);
		String value = preferences.getString(DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS, String.valueOf(DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS_DEFAULT));
		int numberOfPackets = DfuSettingsConstants.SETTINGS_NUMBER_OF_PACKETS_DEFAULT;
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
		int mbrSize = DfuSettingsConstants.SETTINGS_DEFAULT_MBR_SIZE;
		try {
			mbrSize = Integer.parseInt(value);
			if (mbrSize < 0)
				mbrSize = 0;
		} catch (final NumberFormatException e) {
			mbrSize = DfuSettingsConstants.SETTINGS_DEFAULT_MBR_SIZE;
		}

		sendLogBroadcast(Level.VERBOSE, "Starting DFU service");

		InputStream is = null;
		int imageSizeInBytes;
		try {
			// Prepare data to send, calculate stream size
			try {
				sendLogBroadcast(Level.VERBOSE, "Opening file...");
				if (fileUri != null)
					is = openInputStream(fileUri, mimeType, mbrSize, fileType);
				else
					is = openInputStream(filePath, mimeType, mbrSize, fileType);

				mInputStream = is;
				imageSizeInBytes = mImageSizeInBytes = is.available();
				// Update the file type bit field basing on the ZIP content
				if (fileType == TYPE_AUTO && MIME_TYPE_ZIP.equals(mimeType)) {
					final ZipHexInputStream zhis = (ZipHexInputStream) is;
					fileType = zhis.getContentType();
				}
				mFileType = fileType;
				sendLogBroadcast(Level.INFO, "Image file opened (" + mImageSizeInBytes + " bytes in total)");
			} catch (final FileNotFoundException e) {
				loge("An exception occured while opening file", e);
				sendErrorBroadcast(ERROR_FILE_NOT_FOUND);
				return;
			} catch (final IOException e) {
				loge("An exception occured while calculating file size", e);
				sendErrorBroadcast(ERROR_FILE_ERROR);
				return;
			}

			// Let's connect to the device
			sendLogBroadcast(Level.VERBOSE, "Connecting to DFU target...");
			updateProgressNotification(PROGRESS_CONNECTING);

			final BluetoothGatt gatt = connect(deviceAddress);
			// Are we connected?
			if (mErrorState > 0) { // error occurred
				final int error = mErrorState & ~ERROR_CONNECTION_MASK;
				loge("An error occurred while connecting to the device:" + error);
				sendLogBroadcast(Level.ERROR, String.format("Connection failed (0x%02X): %s", error, GattError.parse(error)));
				terminateConnection(gatt, mErrorState);
				return;
			}
			if (mAborted) {
				logi("Upload aborted");
				sendLogBroadcast(Level.WARNING, "Upload aborted");
				terminateConnection(gatt, PROGRESS_ABORTED);
				return;
			}

			// We have connected to DFU device and services are discoverer
			final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID); // there was a case when the service was null. I don't know why
			if (dfuService == null) {
				loge("DFU service does not exists on the device");
				sendLogBroadcast(Level.WARNING, "Connected. DFU Service not found");
				terminateConnection(gatt, ERROR_SERVICE_NOT_FOUND);
				return;
			}
			final BluetoothGattCharacteristic controlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
			final BluetoothGattCharacteristic packetCharacteristic = dfuService.getCharacteristic(DFU_PACKET_UUID);
			if (controlPointCharacteristic == null || packetCharacteristic == null) {
				loge("DFU characteristics not found in the DFU service");
				sendLogBroadcast(Level.WARNING, "Connected. DFU Characteristics not found");
				terminateConnection(gatt, ERROR_CHARACTERISTICS_NOT_FOUND);
				return;
			}

			sendLogBroadcast(Level.INFO, "Connected. Services discovered");
			try {
				// enable notifications
				updateProgressNotification(PROGRESS_STARTING);
				setCharacteristicNotification(gatt, controlPointCharacteristic, true);
				sendLogBroadcast(Level.INFO, "Notifications enabled");

				try {
					// set up the temporary variable that will hold the responses
					byte[] response = null;
					int status = 0;

					/*
					 * DFU v.1 supports updating only an Application.
					 * Initializing procedure:
					 * [DFU Start (0x01)] -> DFU Control Point
					 * [App size in bytes (UINT32)] -> DFU Packet
					 * ---------------------------------------------------------------------
					 * DFU v.2 supports updating Soft Device, Bootloader and Application
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
					 * If DFU target supports only DFU v.1 a response [10, 01, 03] will be send as a notification on DFU Control Point characteristic, where:
					 * 10 - Response for...
					 * 01 - DFU Start command
					 * 03 - Operation Not Supported
					 * (see table below)
					 * In that case:
					 * 1. If this is application update - service will try to upload using DFU v.1
					 * 2. In case of SD or BL update an error is returned
					 */

					// obtain size of image(s)
					int softDeviceImageSize = (fileType & TYPE_SOFT_DEVICE) > 0 ? imageSizeInBytes : 0;
					int bootloaderImageSize = (fileType & TYPE_BOOTLOADER) > 0 ? imageSizeInBytes : 0;
					int appImageSize = (fileType & TYPE_APPLICATION) > 0 ? imageSizeInBytes : 0;
					// the sizes above may be overwritten if a ZIP file was passed
					if (MIME_TYPE_ZIP.equals(mimeType)) {
						final ZipHexInputStream zhis = (ZipHexInputStream) is;
						softDeviceImageSize = zhis.softDeviceImageSize();
						bootloaderImageSize = zhis.bootloaderImageSize();
						appImageSize = zhis.applicationImageSize();
					}

					try {
						OP_CODE_START_DFU[1] = (byte) fileType;

						// send Start DFU command to Control Point
						logi("Sending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU);
						sendLogBroadcast(Level.APPLICATION, "DFU Start sent (Op Code 1, Upload Mode = " + fileType + ")");

						// send image size in bytes to DFU Packet
						logi("Sending image size array to DFU Packet: [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");
						writeImageSize(gatt, packetCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
						sendLogBroadcast(Level.APPLICATION, "Firmware image size sent [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");

						// a notification will come with confirmation. Let's wait for it a bit
						response = readNotificationResponse();

						/*
						 * The response received from the DFU device contains:
						 * +---------+--------+----------------------------------------------------+
						 * | byte no | value | description |
						 * +---------+--------+----------------------------------------------------+
						 * | 0 | 16 | Response code |
						 * | 1 | 1 | The Op Code of a request that this response is for |
						 * | 2 | STATUS | See DFU_STATUS_* for status codes |
						 * +---------+--------+----------------------------------------------------+
						 */
						status = getStatusCode(response, OP_CODE_RECEIVE_START_DFU_KEY);
						sendLogBroadcast(Level.APPLICATION, "Responce received (Op Code: " + response[1] + " Status: " + status + ")");
						if (status != DFU_STATUS_SUCCESS)
							throw new RemoteDfuException("Starting DFU failed", status);
					} catch (final RemoteDfuException e) {
						try {
							if (e.getErrorNumber() != DFU_STATUS_NOT_SUPPORTED)
								throw e;

							// If user wants to send soft device and/or bootloader + application we may try to send the Soft Device/Bootloader files first, 
							// and then reconnect and send the application
							if ((fileType & TYPE_APPLICATION) > 0 && (fileType & (TYPE_SOFT_DEVICE | TYPE_BOOTLOADER)) > 0) {
								logw("DFU target does not support (SD/BL)+App update");
								sendLogBroadcast(Level.WARNING, "DFU target does not support (SD/BL)+App update");

								fileType &= ~TYPE_APPLICATION; // clear application bit
								mFileType = fileType;
								OP_CODE_START_DFU[1] = (byte) fileType;
								mPartsTotal = 2;

								// set new content type in the ZIP Input Stream and update sizes of images
								final ZipHexInputStream zhis = (ZipHexInputStream) is;
								zhis.setContentType(fileType);
								try {
									appImageSize = 0;
									mImageSizeInBytes = is.available();
								} catch (final IOException e1) {
									// never happen
								}

								// send Start DFU command to Control Point
								sendLogBroadcast(Level.VERBOSE, "Sending only SD/BL");
								logi("Resending Start DFU command (Op Code = 1, Upload Mode = " + fileType + ")");
								writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU);
								sendLogBroadcast(Level.APPLICATION, "DFU Start sent (Op Code 1, Upload Mode = " + fileType + ")");

								// send image size in bytes to DFU Packet
								logi("Sending image size array to DFU Packet: [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");
								writeImageSize(gatt, packetCharacteristic, softDeviceImageSize, bootloaderImageSize, appImageSize);
								sendLogBroadcast(Level.APPLICATION, "Firmware image size sent [" + softDeviceImageSize + "b, " + bootloaderImageSize + "b, " + appImageSize + "b]");

								// a notification will come with confirmation. Let's wait for it a bit
								response = readNotificationResponse();
								status = getStatusCode(response, OP_CODE_RECEIVE_START_DFU_KEY);
								sendLogBroadcast(Level.APPLICATION, "Responce received (Op Code: " + response[1] + " Status: " + status + ")");
								if (status != DFU_STATUS_SUCCESS)
									throw new RemoteDfuException("Starting DFU failed", status);
							} else
								throw e;
						} catch (final RemoteDfuException e1) {
							if (e1.getErrorNumber() != DFU_STATUS_NOT_SUPPORTED)
								throw e1;

							// If operation is not supported by DFU target we may try to upload application with legacy mode, using DFU v.1
							if (fileType == TYPE_APPLICATION) {
								// The DFU target does not support DFU v.2 protocol
								logw("DFU target does not support DFU v.2");
								sendLogBroadcast(Level.WARNING, "DFU target does not support DFU v.2");

								// send Start DFU command to Control Point
								sendLogBroadcast(Level.VERBOSE, "Switching to DFU v.1");
								logi("Resending Start DFU command (Op Code = 1)");
								writeOpCode(gatt, controlPointCharacteristic, OP_CODE_START_DFU); // If has 2 bytes, but the second one is ignored
								sendLogBroadcast(Level.APPLICATION, "DFU Start sent (Op Code 1)");

								// send image size in bytes to DFU Packet
								logi("Sending application image size to DFU Packet: " + imageSizeInBytes + " bytes");
								writeImageSize(gatt, packetCharacteristic, mImageSizeInBytes);
								sendLogBroadcast(Level.APPLICATION, "Firmware image size sent (" + imageSizeInBytes + " bytes)");

								// a notification will come with confirmation. Let's wait for it a bit
								response = readNotificationResponse();
								status = getStatusCode(response, OP_CODE_RECEIVE_START_DFU_KEY);
								sendLogBroadcast(Level.APPLICATION, "Responce received (Op Code: " + response[1] + " Status: " + status + ")");
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

					// Send the number of packets of firmware before receiving a receipt notification
					final int numberOfPacketsBeforeNotification = mPacketsBeforeNotification;
					if (numberOfPacketsBeforeNotification > 0) {
						logi("Sending the number of packets before notifications (Op Code = 8, value = " + numberOfPacketsBeforeNotification + ")");
						setNumberOfPackets(OP_CODE_PACKET_RECEIPT_NOTIF_REQ, numberOfPacketsBeforeNotification);
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_PACKET_RECEIPT_NOTIF_REQ);
						sendLogBroadcast(Level.APPLICATION, "Packet Receipt Notif Req (Op Code 8) sent (value: " + numberOfPacketsBeforeNotification + ")");
					}

					// Initialize firmware upload
					logi("Sending Receive Firmware Image request (Op Code = 3)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RECEIVE_FIRMWARE_IMAGE);
					sendLogBroadcast(Level.APPLICATION, "Receive Firmware Image request sent");

					// This allow us to calculate upload time
					final long startTime = mLastProgressTime = mStartTime = SystemClock.elapsedRealtime();
					updateProgressNotification();
					try {
						sendLogBroadcast(Level.APPLICATION, "Starting upload...");
						response = uploadFirmwareImage(gatt, packetCharacteristic, is);
					} catch (final DeviceDisconnectedException e) {
						loge("Disconnected while sending data");
						throw e;
						// TODO reconnect?
					}

					final long endTime = SystemClock.elapsedRealtime();
					logi("Transfer of " + mBytesSent + " bytes has taken " + (endTime - startTime) + " ms");
					sendLogBroadcast(Level.APPLICATION, "Upload completed in " + (endTime - startTime) + " ms");

					// Check the result of the operation
					status = getStatusCode(response, OP_CODE_RECEIVE_FIRMWARE_IMAGE_KEY);
					sendLogBroadcast(Level.APPLICATION, "Responce received (Op Code: " + response[1] + " Status: " + status + ")");
					logi("Response received. Op Code: " + response[0] + " Req Op Code: " + response[1] + " status: " + response[2]);
					if (status != DFU_STATUS_SUCCESS)
						throw new RemoteDfuException("Device returned error after sending file", status);

					// Send Validate request
					logi("Sending Validate request (Op Code = 4)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_VALIDATE);
					sendLogBroadcast(Level.APPLICATION, "Validate request sent");

					// A notification will come with status code. Let's wait for it a bit.
					response = readNotificationResponse();
					status = getStatusCode(response, OP_CODE_RECEIVE_VALIDATE_KEY);
					logi("Response received. Op Code: " + response[0] + " Req Op Code: " + response[1] + " status: " + response[2]);
					sendLogBroadcast(Level.APPLICATION, "Responce received (Op Code: " + response[1] + " Status: " + status + ")");
					if (status != DFU_STATUS_SUCCESS)
						throw new RemoteDfuException("Device returned validation error", status);

					// Disable notifications locally (we don't need to disable them on the device, it will reset)
					updateProgressNotification(PROGRESS_DISCONNECTING);
					gatt.setCharacteristicNotification(controlPointCharacteristic, false);
					sendLogBroadcast(Level.INFO, "Notifications disabled");

					// Send Activate and Reset signal.
					logi("Sending Activate and Reset request (Op Code = 5)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_ACTIVATE_AND_RESET);
					sendLogBroadcast(Level.APPLICATION, "Activate and Reset request sent");

					// The device will reset so we don't have to send Disconnect signal.
					waitUntilDisconnected();
					sendLogBroadcast(Level.INFO, "Disconnected by remote device");

					// Close the device
					refreshDeviceCache(gatt);
					close(gatt);

					/*
					 * We need to send PROGRESS_COMPLETED message only when all files has been transmitted.
					 * In case user wants to send Soft Device and/or Bootloader and Application service will be started twice: one to send SD+BL,
					 * second time to send Application using the new Bootloader. In the first case we do not send PROGRESS_COMPLETED notification.
					 */
					if (mPartCurrent == mPartsTotal) {
						updateProgressNotification(PROGRESS_COMPLETED);
					} else {
						/*
						 * The current service handle will try to upload Soft Device and/or Bootloader.
						 * We need to enqueue another Intent that will try to send application only.
						 */
						logi("Starting service that will upload application");
						final Intent newIntent = new Intent();
						newIntent.fillIn(intent, Intent.FILL_IN_COMPONENT | Intent.FILL_IN_PACKAGE);
						newIntent.putExtra(EXTRA_FILE_TYPE, TYPE_APPLICATION);
						newIntent.putExtra(EXTRA_PART_CURRENT, mPartCurrent + 1);
						newIntent.putExtra(EXTRA_PARTS_TOTAL, mPartsTotal);
						startService(newIntent);
					}
				} catch (final UnknownResponseException e) {
					final int error = ERROR_INVALID_RESPONSE;
					loge(e.getMessage());
					sendLogBroadcast(Level.ERROR, e.getMessage());

					logi("Sending Reset command (Op Code = 6)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
					sendLogBroadcast(Level.APPLICATION, "Reset request sent");
					terminateConnection(gatt, error);
				} catch (final RemoteDfuException e) {
					final int error = ERROR_REMOTE_MASK | e.getErrorNumber();
					loge(e.getMessage());
					sendLogBroadcast(Level.ERROR, String.format("Remote DFU error: %s", GattError.parse(error)));

					logi("Sending Reset command (Op Code = 6)");
					writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
					sendLogBroadcast(Level.APPLICATION, "Reset request sent");
					terminateConnection(gatt, error);
				}
			} catch (final UploadAbortedException e) {
				logi("Upload aborted");
				sendLogBroadcast(Level.WARNING, "Upload aborted");
				if (mConnectionState == STATE_CONNECTED_AND_READY)
					try {
						mAborted = false;
						logi("Sending Reset command (Op Code = 6)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
						sendLogBroadcast(Level.APPLICATION, "Reset request sent");
					} catch (final Exception e1) {
						// do nothing
					}
				terminateConnection(gatt, PROGRESS_ABORTED);
			} catch (final DeviceDisconnectedException e) {
				sendLogBroadcast(Level.ERROR, "Device has disconneted");
				// TODO reconnect n times?
				loge(e.getMessage());
				if (mNotificationsEnabled)
					gatt.setCharacteristicNotification(controlPointCharacteristic, false);
				close(gatt);
				updateProgressNotification(ERROR_DEVICE_DISCONNECTED);
			} catch (final DfuException e) {
				final int error = e.getErrorNumber() & ~ERROR_CONNECTION_MASK;
				sendLogBroadcast(Level.ERROR, String.format("Error (0x%02X): %s", error, GattError.parse(error)));
				loge(e.getMessage());
				if (mConnectionState == STATE_CONNECTED_AND_READY)
					try {
						logi("Sending Reset command (Op Code = 6)");
						writeOpCode(gatt, controlPointCharacteristic, OP_CODE_RESET);
						sendLogBroadcast(Level.APPLICATION, "Reset request sent");
					} catch (final Exception e1) {
						// do nothing
					}
				terminateConnection(gatt, e.getErrorNumber());
			}
		} finally {
			try {
				// upload has finished (success of fail)
				editor.putBoolean(DFU_IN_PROGRESS, false);
				editor.commit();

				// ensure that input stream is always closed
				mInputStream = null;
				if (is != null)
					is.close();
				is = null;
			} catch (final IOException e) {
				// do nothing
			}
		}
	}

	/**
	 * Sets number of data packets that will be send before the notification will be received.
	 * 
	 * @param data
	 *            control point data packet
	 * @param value
	 *            number of packets before receiving notification. If this value is 0, then the notification of packet receipt will be disabled by the DFU target.
	 */
	private void setNumberOfPackets(final byte[] data, final int value) {
		data[1] = (byte) (value & 0xFF);
		data[2] = (byte) ((value >> 8) & 0xFF);
	}

	/**
	 * Opens the binary input stream that returns the firmware image content. A Path to the file is given.
	 * 
	 * @param filePath
	 *            the path to the HEX file
	 * @param mimeType
	 *            the file type
	 * @param mbrSize
	 *            the size of MBR, by default 0x1000
	 * @param types
	 *            the content files types in ZIP
	 * @return the input stream with binary image content
	 */
	private InputStream openInputStream(final String filePath, final String mimeType, final int mbrSize, final int types) throws FileNotFoundException, IOException {
		final InputStream is = new FileInputStream(filePath);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ZipHexInputStream(is, mbrSize, types);
		return new HexInputStream(is, mbrSize);
	}

	/**
	 * Opens the binary input stream. A Uri to the stream is given.
	 * 
	 * @param stream
	 *            the Uri to the stream
	 * @param mimeType
	 *            the file type
	 * @param mbrSize
	 *            the size of MBR, by default 0x1000
	 * @param types
	 *            the content files types in ZIP
	 * @return the input stream with binary image content
	 */
	private InputStream openInputStream(final Uri stream, final String mimeType, final int mbrSize, final int types) throws FileNotFoundException, IOException {
		final InputStream is = getContentResolver().openInputStream(stream);
		if (MIME_TYPE_ZIP.equals(mimeType))
			return new ZipHexInputStream(is, mbrSize, types);
		return new HexInputStream(is, mbrSize);
	}

	/**
	 * Connects to the BLE device with given address. This method is SYNCHRONOUS, it wait until the connection status change from {@link #STATE_CONNECTING} to {@link #STATE_CONNECTED_AND_READY} or an
	 * error occurs.
	 * 
	 * @param address
	 *            the device address
	 * @return the GATT device
	 */
	private BluetoothGatt connect(final String address) {
		mConnectionState = STATE_CONNECTING;

		logi("Connecting to the device...");
		final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		final BluetoothGatt gatt = device.connectGatt(this, false, mGattCallback);

		// We have to wait until the device is connected and services are discovered
		// Connection error may occur as well.
		try {
			synchronized (mLock) {
				while (((mConnectionState == STATE_CONNECTING || mConnectionState == STATE_CONNECTED) && mErrorState == 0 && !mAborted) || mPaused)
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
	 * @param gatt
	 *            the GATT device to be disconnected
	 * @param error
	 *            error number
	 */
	private void terminateConnection(final BluetoothGatt gatt, final int error) {
		if (mConnectionState != STATE_DISCONNECTED) {
			updateProgressNotification(PROGRESS_DISCONNECTING);

			// disable notifications
			try {
				final BluetoothGattService dfuService = gatt.getService(DFU_SERVICE_UUID);
				if (dfuService != null) {
					final BluetoothGattCharacteristic controlPointCharacteristic = dfuService.getCharacteristic(DFU_CONTROL_POINT_UUID);
					setCharacteristicNotification(gatt, controlPointCharacteristic, false);
					sendLogBroadcast(Level.INFO, "Notifications disabled");
				}
			} catch (final DeviceDisconnectedException e) {
				// do nothing
			} catch (final DfuException e) {
				// do nothing
			} catch (final Exception e) {
				// do nothing
			}

			// Disconnect from the device
			disconnect(gatt);
			sendLogBroadcast(Level.INFO, "Disconnected");
		}

		// Close the device
		refreshDeviceCache(gatt);
		close(gatt);
		updateProgressNotification(error);
	}

	/**
	 * Disconnects from the device. This is SYNCHRONOUS method and waits until the callback returns new state. Terminates immediately if device is already disconnected. Do not call this method
	 * directly, use {@link #terminateConnection(BluetoothGatt, int)} instead.
	 * 
	 * @param gatt
	 *            the GATT device that has to be disconnected
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
				while (mConnectionState != STATE_DISCONNECTED && mErrorState == 0)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
	}

	/**
	 * Closes the GATT device and cleans up.
	 * 
	 * @param gatt
	 *            the GATT device to be closed
	 */
	private void close(final BluetoothGatt gatt) {
		logi("Cleaning up...");
		gatt.close();
		mConnectionState = STATE_CLOSED;
	}

	/**
	 * Clears the device cache. After uploading new firmware the DFU target will have other services than before.
	 * 
	 * @param gatt
	 *            the GATT device to be refreshed
	 */
	private void refreshDeviceCache(final BluetoothGatt gatt) {
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
			loge("An exception occured while refreshing device", e);
		}
	}

	/**
	 * Checks whether the response received is valid and returns the status code.
	 * 
	 * @param response
	 *            the response received from the DFU device.
	 * @param request
	 *            the expected Op Code
	 * @return the status code
	 * @throws UnknownResponseException
	 *             if response was not valid
	 */
	private int getStatusCode(final byte[] response, final int request) throws UnknownResponseException {
		if (response == null || response.length != 3 || response[0] != OP_CODE_RESPONSE_CODE_KEY || response[1] != request || response[2] < 1 || response[2] > 6)
			throw new UnknownResponseException("Invalid response received", response, request);
		return response[2];
	}

	/**
	 * Enables or disables the notifications for given characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link BluetoothGattCallback#onDescriptorWrite(BluetoothGatt, BluetoothGattDescriptor, int)} will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}. If
	 * connection state will change, or an error will occur, an exception will be thrown.
	 * 
	 * @param gatt
	 *            the GATT device
	 * @param characteristic
	 *            the characteristic to enable or disable notifications for
	 * @param enable
	 *            <code>true</code> to enable notifications, <code>false</code> to disable them
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void setCharacteristicNotification(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final boolean enable) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to set notifications state", mConnectionState);
		mErrorState = 0;

		if (mNotificationsEnabled == enable)
			return;

		logi((enable ? "Enabling " : "Disabling") + " notifications...");

		if (enable) {
			sendLogBroadcast(Level.VERBOSE, "Enabling notifications for " + characteristic.getUuid());
		} else {
			sendLogBroadcast(Level.VERBOSE, "Disabling notifications for " + characteristic.getUuid());
		}
		// enable notifications locally
		gatt.setCharacteristicNotification(characteristic, enable);

		// enable notifications on the device
		final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
		descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
		sendLogBroadcast(Level.DEBUG, "gatt.writeDescriptor(" + descriptor.getUuid() + (enable ? ", value=0x01-00)" : ", value=0x00-00)"));
		gatt.writeDescriptor(descriptor);

		// We have to wait until device gets disconnected or an error occur
		try {
			synchronized (mLock) {
				while ((mNotificationsEnabled != enable && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Unable to set notifications state", mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to set notifications state", mConnectionState);
	}

	/**
	 * Writes the operation code to the characteristic. This method is SYNCHRONOUS and wait until the
	 * {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)} will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}.
	 * If connection state will change, or an error will occur, an exception will be thrown.
	 * 
	 * @param gatt
	 *            the GATT device
	 * @param characteristic
	 *            the characteristic to write to. Should be the DFU CONTROL POINT
	 * @param value
	 *            the value to write to the characteristic
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeOpCode(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final byte[] value) throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		mReceivedData = null;
		mErrorState = 0;
		mRequestCompleted = false;
		/*
		 * Sending a command that will make the DFU target to reboot may cause an error 133 (0x85 - Gatt Error). If so, with this flag set, the error will not be shown to the user
		 * as the peripheral is disconnected anyway. See: mGattCallback#onCharacteristicWrite(...) method
		 */
		mResetRequestSent = value[0] == OP_CODE_RECEIVE_RESET_KEY || value[0] == OP_CODE_RECEIVE_ACTIVATE_AND_RESET_KEY;

		characteristic.setValue(value);
		sendLogBroadcast(Level.VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(Level.DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mRequestCompleted == false && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Unable to write Op Code " + value[0], mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Op Code " + value[0], mConnectionState);
	}

	/**
	 * Writes the image size to the characteristic. This method is SYNCHRONOUS and wait until the {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)}
	 * will be called or the connection state will change from {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 * 
	 * @param gatt
	 *            the GATT device
	 * @param characteristic
	 *            the characteristic to write to. Should be the DFU PACKET
	 * @param imageSize
	 *            the image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int imageSize) throws DeviceDisconnectedException, DfuException,
			UploadAbortedException {
		mReceivedData = null;
		mErrorState = 0;
		mImageSizeSent = false;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[4]);
		characteristic.setValue(imageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		sendLogBroadcast(Level.VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(Level.DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeSent == false && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Unable to write Image Size", mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Image Size", mConnectionState);
	}

	/**
	 * <p>
	 * Writes the Soft Device, Bootloader and Application image sizes to the characteristic. Soft Device and Bootloader update was added in DFU v.2. Sizes of SD, BL and App are uploaded as 3x UINT32
	 * even though some of them may be 0s. F.e. if only App is being updated the data will be <0x00000000, 0x00000000, [App size]>
	 * </p>
	 * <p>
	 * This method is SYNCHRONOUS and wait until the {@link BluetoothGattCallback#onCharacteristicWrite(BluetoothGatt, BluetoothGattCharacteristic, int)} will be called or the connection state will
	 * change from {@link #STATE_CONNECTED_AND_READY}. If connection state will change, or an error will occur, an exception will be thrown.
	 * </p>
	 * 
	 * @param gatt
	 *            the GATT device
	 * @param characteristic
	 *            the characteristic to write to. Should be the DFU PACKET
	 * @param softDeviceImageSize
	 *            the Soft Device image size in bytes
	 * @param bootloaderImageSize
	 *            the Bootloader image size in bytes
	 * @param appImageSize
	 *            the Application image size in bytes
	 * @throws DeviceDisconnectedException
	 * @throws DfuException
	 * @throws UploadAbortedException
	 */
	private void writeImageSize(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int softDeviceImageSize, final int bootloaderImageSize, final int appImageSize)
			throws DeviceDisconnectedException, DfuException, UploadAbortedException {
		mReceivedData = null;
		mErrorState = 0;
		mImageSizeSent = false;

		characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
		characteristic.setValue(new byte[12]);
		characteristic.setValue(softDeviceImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 0);
		characteristic.setValue(bootloaderImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 4);
		characteristic.setValue(appImageSize, BluetoothGattCharacteristic.FORMAT_UINT32, 8);
		sendLogBroadcast(Level.VERBOSE, "Writing to characteristic " + characteristic.getUuid());
		sendLogBroadcast(Level.DEBUG, "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
		gatt.writeCharacteristic(characteristic);

		// We have to wait for confirmation
		try {
			synchronized (mLock) {
				while ((mImageSizeSent == false && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Unable to write Image Sizes", mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Image Sizes", mConnectionState);
	}

	/**
	 * Starts sending the data. This method is SYNCHRONOUS and terminates when the whole file will be uploaded or the connection status will change from {@link #STATE_CONNECTED_AND_READY}. If
	 * connection state will change, or an error will occur, an exception will be thrown.
	 * 
	 * @param gatt
	 *            the GATT device (DFU target)
	 * @param packetCharacteristic
	 *            the characteristic to write file content to. Must be the DFU PACKET
	 * @return The response value received from notification with Op Code = 3 when all bytes will be uploaded successfully.
	 * @throws DeviceDisconnectedException
	 *             Thrown when the device will disconnect in the middle of the transmission. The error core will be saved in {@link #mConnectionState}.
	 * @throws DfuException
	 *             Thrown if DFU error occur
	 * @throws UploadAbortedException
	 */
	private byte[] uploadFirmwareImage(final BluetoothGatt gatt, final BluetoothGattCharacteristic packetCharacteristic, final InputStream inputStream) throws DeviceDisconnectedException,
			DfuException, UploadAbortedException {
		mReceivedData = null;
		mErrorState = 0;

		final byte[] buffer = mBuffer;
		try {
			final int size = inputStream.read(buffer);
			sendLogBroadcast(Level.VERBOSE, "Sending firmware to characteristic " + packetCharacteristic.getUuid() + "...");
			writePacket(gatt, packetCharacteristic, buffer, size);
		} catch (final HexFileValidationException e) {
			throw new DfuException("HEX file not valid", ERROR_FILE_INVALID);
		} catch (final IOException e) {
			throw new DfuException("Error while reading file", ERROR_FILE_IO_EXCEPTION);
		}

		try {
			synchronized (mLock) {
				while ((mReceivedData == null && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Uploading Fimrware Image failed", mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Uploading Fimrware Image failed: device disconnected", mConnectionState);

		return mReceivedData;
	}

	/**
	 * Writes the buffer to the characteristic. The maximum size of the buffer is 20 bytes. This method is ASYNCHRONOUS and returns immediately after adding the data to TX queue.
	 * 
	 * @param gatt
	 *            the GATT device
	 * @param characteristic
	 *            the characteristic to write to. Should be the DFU PACKET
	 * @param buffer
	 *            the buffer with 1-20 bytes
	 * @param size
	 *            the number of bytes from the buffer to send
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
		// More info: this works fine on Nexus 5 (Andorid 4.4) (4.3 seconds) and on Samsung S4 (Android 4.3) (20 seconds) so this is a driver issue.
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
		mErrorState = 0;
		try {
			synchronized (mLock) {
				while ((mReceivedData == null && mConnectionState == STATE_CONNECTED_AND_READY && mErrorState == 0 && !mAborted) || mPaused)
					mLock.wait();
			}
		} catch (final InterruptedException e) {
			loge("Sleeping interrupted", e);
		}
		if (mAborted)
			throw new UploadAbortedException();
		if (mErrorState != 0)
			throw new DfuException("Unable to write Op Code", mErrorState);
		if (mConnectionState != STATE_CONNECTED_AND_READY)
			throw new DeviceDisconnectedException("Unable to write Op Code", mConnectionState);
		return mReceivedData;
	}

	/** Stores the last progress percent. Used to lower number of calls of {@link #updateProgressNotification(int)}. */
	private int mLastProgress = -1;

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
	 * @param progress
	 *            the current progress state or an error number, can be one of {@link #PROGRESS_CONNECTING}, {@link #PROGRESS_STARTING}, {@link #PROGRESS_VALIDATING}, {@link #PROGRESS_DISCONNECTING},
	 *            {@link #PROGRESS_COMPLETED} or {@link #ERROR_FILE_ERROR}, {@link #ERROR_FILE_INVALID} , etc
	 */
	private void updateProgressNotification(final int progress) {
		final String deviceAddress = mDeviceAddress;
		final String deviceName = mDeviceName != null ? mDeviceName : getString(R.string.dfu_unknown_name);

		final Bitmap largeIcon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_stat_notify_dfu);

		final Notification.Builder builder = new Notification.Builder(this).setSmallIcon(android.R.drawable.stat_sys_upload).setOnlyAlertOnce(true).setLargeIcon(largeIcon);
		switch (progress) {
		case PROGRESS_CONNECTING:
			builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_connecting)).setContentText(getString(R.string.dfu_status_connecting_msg, deviceName)).setProgress(100, 0, true);
			break;
		case PROGRESS_STARTING:
			builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_starting)).setContentText(getString(R.string.dfu_status_starting_msg, deviceName)).setProgress(100, 0, true);
			break;
		case PROGRESS_VALIDATING:
			builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_validating)).setContentText(getString(R.string.dfu_status_validating_msg, deviceName)).setProgress(100, 0, true);
			break;
		case PROGRESS_DISCONNECTING:
			builder.setOngoing(true).setContentTitle(getString(R.string.dfu_status_disconnecting)).setContentText(getString(R.string.dfu_status_disconnecting_msg, deviceName))
					.setProgress(100, 0, true);
			break;
		case PROGRESS_COMPLETED:
			builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_completed)).setContentText(getString(R.string.dfu_status_completed_msg)).setAutoCancel(true);
			break;
		case PROGRESS_ABORTED:
			builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_aborted)).setContentText(getString(R.string.dfu_status_aborted_msg)).setAutoCancel(true);
			break;
		default:
			if (progress >= ERROR_MASK) {
				// progress is an error number
				builder.setOngoing(false).setContentTitle(getString(R.string.dfu_status_error)).setContentText(getString(R.string.dfu_status_error_msg)).setAutoCancel(true);
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
		if (mLogSession != null)
			intent.putExtra(EXTRA_LOG_URI, mLogSession.getSessionUri());
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
	 * The method must return the activity class that will be used to create the pending intent used as a content intent in the notification showing the upload progress. The activity will be launched
	 * when user click the notification. DfuService will add {@link Intent#FLAG_ACTIVITY_NEW_TASK} flag and the following extras:
	 * <ul>
	 * <li>{@link #EXTRA_DEVICE_ADDRESS} - target device address</li>
	 * <li>{@link #EXTRA_DEVICE_NAME} - target device name</li>
	 * <li>{@link #EXTRA_DEVICE_PROGRESS} - the connection state (values < 0)*, current progress (0-100) or error number if {@link #ERROR_MASK} bit set.</li>
	 * </ul>
	 * If the nRF Logger session {@link Uri} was passed to create a service it will also be added as an extra:
	 * <ul>
	 * <li>{@link #EXTRA_LOG_URI} - the log session Uri</li>
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
	 * <li>{@link #PROGRESS_VALIDATING}</li>
	 * </ul>
	 * </p>
	 * 
	 * @return the target activity class
	 */
	protected abstract Class<? extends Activity> getNotificationTarget();

	private void sendProgressBroadcast(final int progress) {
		final long now = SystemClock.elapsedRealtime();
		final float speed = (float) (mBytesSent - mLastBytesSent) / (float) (now - mLastProgressTime);
		final float avgSpeed = (float) mBytesSent / (float) (now - mStartTime);
		mLastProgressTime = now;
		mLastBytesSent = mBytesSent;

		final Intent broadcast = new Intent(BROADCAST_PROGRESS);
		broadcast.putExtra(EXTRA_DATA, progress);
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		broadcast.putExtra(EXTRA_PART_CURRENT, mPartCurrent);
		broadcast.putExtra(EXTRA_PARTS_TOTAL, mPartsTotal);
		broadcast.putExtra(EXTRA_SPEED_B_PER_MS, speed);
		broadcast.putExtra(EXTRA_AVG_SPEED_B_PER_MS, avgSpeed);
		if (mLogSession != null)
			broadcast.putExtra(EXTRA_LOG_URI, mLogSession.getSessionUri());
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	private void sendErrorBroadcast(final int error) {
		final Intent broadcast = new Intent(BROADCAST_ERROR);
		broadcast.putExtra(EXTRA_DATA, error & ~ERROR_CONNECTION_MASK);
		broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
		if (mLogSession != null)
			broadcast.putExtra(EXTRA_LOG_URI, mLogSession.getSessionUri());
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
	}

	private void sendLogBroadcast(final int level, final String message) {
		final ILogSession session = mLogSession;
		final String fullMessage = "[DFU] " + message;
		if (session == null) {
			// the log provider is not installed, use broadcast action 
			final Intent broadcast = new Intent(BROADCAST_LOG);
			broadcast.putExtra(EXTRA_LOG_MESSAGE, fullMessage);
			broadcast.putExtra(EXTRA_LOG_LEVEL, level);
			broadcast.putExtra(EXTRA_DEVICE_ADDRESS, mDeviceAddress);
			LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
		} else {
			// the log provider is installed, we can use logger
			Logger.log(session, level, fullMessage);
		}
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

	private static IntentFilter makeDfuActionIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(DfuBaseService.BROADCAST_ACTION);
		return intentFilter;
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
}
