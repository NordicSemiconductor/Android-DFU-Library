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

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.ParcelUuid;
import android.os.Parcelable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;

import java.security.InvalidParameterException;
import java.util.UUID;

/**
 * Starting the DfuService service requires a knowledge of some EXTRA_* constants used to pass
 * parameters to the service. The DfuServiceInitiator class may be used to make this process easier.
 * It provides simple API that covers all low lever operations.
 */
@SuppressWarnings({"WeakerAccess", "unused", "deprecation"})
public final class DfuServiceInitiator {
	public static final int DEFAULT_PRN_VALUE = 12;
	public static final int DEFAULT_MBR_SIZE = 0x1000;

	/** Constant used to narrow the scope of the update to system components (SD+BL) only. */
	public static final int SCOPE_SYSTEM_COMPONENTS = 1;
	/** Constant used to narrow the scope of the update to application only. */
	public static final int SCOPE_APPLICATION = 2;

	private final String deviceAddress;
	private String deviceName;

	private boolean disableNotification = false;
	private boolean startAsForegroundService = true;

	private Uri fileUri;
	private String filePath;
	private int fileResId;

	private Uri initFileUri;
	private String initFilePath;
	private int initFileResId;

	private String mimeType;
	private int fileType = -1;

	private boolean keepBond;
	private boolean restoreBond;
	private boolean forceDfu = false;
	private boolean enableUnsafeExperimentalButtonlessDfu = false;
	private boolean disableResume = false;
	private int numberOfRetries = 0; // 0 to be backwards compatible
	private int mbrSize = DEFAULT_MBR_SIZE;
	private long dataObjectDelay = 0; // initially disabled

	private Boolean packetReceiptNotificationsEnabled;
	private int numberOfPackets = 12;

	private int mtu = 517;
	private int currentMtu = 23;

	private Parcelable[] legacyDfuUuids;
	private Parcelable[] secureDfuUuids;
	private Parcelable[] experimentalButtonlessDfuUuids;
	private Parcelable[] buttonlessDfuWithoutBondSharingUuids;
	private Parcelable[] buttonlessDfuWithBondSharingUuids;

	/**
	 * Creates the builder. Use setZip(...), or setBinOrHex(...) methods to specify the file you
	 * want to upload. In the latter case an init file may also be set using the setInitFile(...)
	 * method. Init files are required by DFU Bootloader version 0.5 or newer (SDK 7.0.0+).
	 *
	 * @param deviceAddress the target device device address
	 */
	public DfuServiceInitiator(@NonNull final String deviceAddress) {
		this.deviceAddress = deviceAddress;
	}

	/**
	 * Sets the device name. The device name is not required. It's written in the notification
	 * during the DFU process. If not set the
	 * {@link no.nordicsemi.android.dfu.R.string#dfu_unknown_name R.string.dfu_unknown_name}
	 * value will be used.
	 *
	 * @param name the device name (optional)
	 * @return the builder
	 */
	public DfuServiceInitiator setDeviceName(@Nullable final String name) {
		this.deviceName = name;
		return this;
	}

	/**
	 * Sets whether the progress notification in the status bar should be disabled.
	 * Defaults to false.
	 *
	 * @param disableNotification whether to disable the notification
	 * @return the builder
	 */
	public DfuServiceInitiator setDisableNotification(final boolean disableNotification) {
		this.disableNotification = disableNotification;
		return this;
	}

	/**
	 * Sets whether the DFU service should be started as a foreground service. By default it's
	 * <i>true</i>. According to
	 * <a href="https://developer.android.com/about/versions/oreo/background.html">
	 * https://developer.android.com/about/versions/oreo/background.html</a>
	 * the background service may be killed by the system on Android Oreo after user quits the
	 * application so it is recommended to keep it as a foreground service (default) at least on
	 * Android Oreo+.
	 *
	 * @param foreground whether the service should be started in foreground state.
	 * @return the builder
	 */
	public DfuServiceInitiator setForeground(final boolean foreground) {
		this.startAsForegroundService = foreground;
		return this;
	}

	/**
	 * Sets whether the bond information should be preserver after flashing new application.
	 * This feature requires DFU Bootloader version 0.6 or newer (SDK 8.0.0+).
	 * Please see the {@link DfuBaseService#EXTRA_KEEP_BOND} for more information regarding
	 * requirements. Remember that currently updating the Soft Device will remove the bond
	 * information.
	 * <p>
	 * This flag is ignored when Secure DFU Buttonless Service is used. It will keep or remove the
	 * bond depending on the Buttonless service type.
	 *
	 * @param keepBond whether the bond information should be preserved in the new application.
	 * @return the builder
	 */
	public DfuServiceInitiator setKeepBond(final boolean keepBond) {
		this.keepBond = keepBond;
		return this;
	}

	/**
	 * Sets whether the bond should be created after the DFU is complete.
	 * Please see the {@link DfuBaseService#EXTRA_RESTORE_BOND} for more information regarding
	 * requirements.
	 * <p>
	 * This flag is ignored when Secure DFU Buttonless Service is used. It will keep or will not
	 * restore the bond depending on the Buttonless service type.
	 *
	 * @param restoreBond whether the bond should be created after the DFU is complete.
	 * @return the builder
	 */
	public DfuServiceInitiator setRestoreBond(final boolean restoreBond) {
		this.restoreBond = restoreBond;
		return this;
	}

	/**
	 * This method sets the duration of a delay, that the service will wait before sending each
	 * data object in Secure DFU. The delay will be done after a data object is created, and before
	 * any data byte is sent. The default value is 0, which disables this feature.
	 * <p>
	 * It has been found, that a delay of at least 300ms reduces the risk of packet lose (the
	 * bootloader needs some time to prepare flash memory) on DFU bootloader from SDK 15 and 16.
	 * The delay does not have to be longer than 400 ms, as according to performed tests, such delay
	 * is sufficient.
	 * <p>
	 * The longer the delay, the more time DFU will take to complete (delay will be repeated for
	 * each data object (4096 bytes)). However, with too small delay a packet lose may occur,
	 * causing the service to enable PRN and set them to 1 making DFU process very, very slow
	 * (but reliable).
	 *
	 * @param delay the initial delay that the service will wait before sending each data object.
	 * @since 1.10
	 * @return the builder
	 */
	public DfuServiceInitiator setPrepareDataObjectDelay(final long delay) {
		this.dataObjectDelay = delay;
		return this;
	}

	/**
	 * Enables or disables the Packet Receipt Notification (PRN) procedure.
	 * <p>
	 * By default the PRNs are disabled on devices with Android Marshmallow or newer and enabled on
	 * older ones.
	 *
	 * @param enabled true to enabled PRNs, false to disable
	 * @return the builder
	 * @see DfuSettingsConstants#SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED
	 */
	public DfuServiceInitiator setPacketsReceiptNotificationsEnabled(final boolean enabled) {
		this.packetReceiptNotificationsEnabled = enabled;
		return this;
	}

	/**
	 * If Packet Receipt Notification procedure is enabled, this method sets number of packets to
	 * be sent before receiving a PRN. A PRN is used to synchronize the transmitter and receiver.
	 * <p>
	 * If the value given is equal to 0, the {@link #DEFAULT_PRN_VALUE} will be used instead.
	 * <p>
	 * To disable PRNs use {@link #setPacketsReceiptNotificationsEnabled(boolean)}.
	 *
	 * @param number number of packets to be sent before receiving a PRN. Defaulted when set to 0.
	 * @return the builder
	 * @see #setPacketsReceiptNotificationsEnabled(boolean)
	 * @see DfuSettingsConstants#SETTINGS_NUMBER_OF_PACKETS
	 */
	public DfuServiceInitiator setPacketsReceiptNotificationsValue(@IntRange(from = 0) final int number) {
		this.numberOfPackets = number > 0 ? number : DEFAULT_PRN_VALUE;
		return this;
	}

	/**
	 * Setting force DFU to true will prevent from jumping to the DFU Bootloader
	 * mode in case there is no DFU Version characteristic (Legacy DFU only!).
	 * Use it if the DFU operation can be handled by your device running in the application mode.
	 * <p>
	 * If the DFU Version characteristic exists, the
	 * information whether to begin DFU operation, or jump to bootloader, is taken from that
	 * characteristic's value. The value returned equal to 0x0100 (read as: minor=1, major=0, or
	 * version 0.1) means that the device is in the application mode and buttonless jump to
	 * DFU Bootloader is supported.
	 * <p>
	 * However, if there is no DFU Version characteristic, a device
	 * may support only Application update (version from SDK 4.3.0), may support Soft Device,
	 * Bootloader and Application update but without buttonless jump to bootloader (SDK 6.0.0)
	 * or with buttonless jump (SDK 6.1.0).
	 * <p>
	 * In the last case, the DFU Library determines whether the device is in application mode or in
	 * DFU Bootloader mode by counting number of services: if no DFU Service found - device is in
	 * app mode and does not support buttonless jump, if the DFU Service is the only service found
	 * (except General Access and General Attribute services) - it assumes it is in DFU Bootloader
	 * mode and may start DFU immediately, if there is at least one service except DFU Service -
	 * the device is in application mode and supports buttonless jump. In the last case, if you
	 * want to perform DFU operation without jumping - call the {@link #setForceDfu(boolean)}
	 * method with parameter equal to true.
	 * <p>
	 * This method is ignored in Secure DFU.
	 *
	 * @param force true to ensure the DFU will start if there is no DFU Version characteristic
	 *              (Legacy DFU only)
	 * @return the builder
	 * @see DfuSettingsConstants#SETTINGS_ASSUME_DFU_NODE
	 */
	@SuppressWarnings("JavaDoc")
	public DfuServiceInitiator setForceDfu(final boolean force) {
		this.forceDfu = force;
		return this;
	}

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
	 *
	 * @return the builder
	 */
	public DfuServiceInitiator disableResume() {
		this.disableResume = true;
		return this;
	}

	/**
	 * Sets the number of retries that the DFU service will use to complete DFU. The default
	 * value is 0, for backwards compatibility reason.
	 * <p>
	 * If the given value is greater than 0, the service will restart itself at most {@code max}
	 * times in case of an undesired disconnection during DFU operation. This attempt counter
	 * is independent from another counter, for reconnection attempts, which is equal to 3.
	 * The latter one will be used when connection will fail with an error (possible packet
	 * collision or any other reason). After successful connection, the reconnection counter is
	 * reset, while the retry counter is cleared after a DFU finishes with success.
	 * <p>
	 * The service will not try to retry DFU in case of any other error, for instance an error
	 * sent from the target device.
	 *
	 * @param max Maximum number of retires to complete DFU. Usually around 2.
	 * @return the builder
	 */
	public DfuServiceInitiator setNumberOfRetries(@IntRange(from = 0) final int max) {
		this.numberOfRetries = max;
		return this;
	}

	/**
	 * Sets the Maximum Transfer Unit (MTU) value that the Secure DFU service will try to request
	 * before performing DFU. By default, value 517 will be used, which is the highest supported
	 * by Android. However, as the highest supported MTU by the Secure DFU from SDK 15
	 * (first which supports higher MTU) is 247, the sides will agree on using MTU = 247 instead
	 * if the phone supports it (Lollipop or newer device).
	 * <p>
	 * The higher the MTU, the faster the data may be sent.
	 * <p>
	 * If you encounter problems with high MTU, you may lower the required value using this method.
	 * See: https://github.com/NordicSemiconductor/Android-DFU-Library/issues/111
	 * <p>
	 * To disable requesting MTU, use value 0, or {@link #disableMtuRequest()}.
	 * <p>
	 * Note: Higher (that is greater then 23) MTUs are supported on Lollipop or newer Android
	 * devices, and on DFU bootloader from SDK 15 or newer (Secure DFU only).
	 *
	 * @param mtu the MTU that wil be requested, 0 to disable MTU request.
	 * @return the builder
	 */
	public DfuServiceInitiator setMtu(@IntRange(from = 23, to = 517) final int mtu) {
		this.mtu = mtu;
		return this;
	}

	/**
	 * Sets the current MTU value. This method should be used only if the device is already
	 * connected and MTU has been requested before DFU service is started.
	 * The SoftDevice allows to change MTU only once, while the following requests fail with
	 * Invalid PDU error. In case this error is received, the MTU will be set to the value
	 * specified using this method. There is no verification of this value. If it's set to
	 * too high value, some of the packets will not be sent and DFU will not succeed.
	 * <p>
	 * By default value 23 is used for compatibility reasons.
	 * <p>
	 * Higher MTU values were supported since SDK 15.0.
	 *
	 * @param mtu the MTU value received in
	 *            {@link android.bluetooth.BluetoothGattCallback#onMtuChanged(BluetoothGatt, int, int)} or
	 *            {@link android.bluetooth.BluetoothGattServerCallback#onMtuChanged(BluetoothDevice, int)}.
	 * @return the builder
	 */
	public DfuServiceInitiator setCurrentMtu(@IntRange(from = 23, to = 517) final int mtu) {
		this.currentMtu = mtu;
		return this;
	}

	/**
	 * Disables MTU request.
	 *
	 * @return the builder
	 * @see #setMtu(int)
	 */
	public DfuServiceInitiator disableMtuRequest() {
		this.mtu = 0;
		return this;
	}

	/**
	 * This method allows to narrow the update to selected parts from the ZIP, for example
	 * to allow only application update from a ZIP file that has SD+BL+App. System components scope
	 * include the Softdevice and/or the Bootloader (they can't be separated as they are packed in
	 * a single bin file and the library does not know whether it contains only the softdevice,
	 * the bootloader or both) Application scope includes the application only.
	 *
	 * @param scope the update scope, one of {@link #SCOPE_SYSTEM_COMPONENTS} or
	 *              {@link #SCOPE_APPLICATION}.
	 * @return the builder
	 */
	public DfuServiceInitiator setScope(@DfuScope final int scope) {
		if (!DfuBaseService.MIME_TYPE_ZIP.equals(mimeType))
			throw new UnsupportedOperationException("Scope can be set only for a ZIP file");
		if (scope == SCOPE_APPLICATION)
			fileType = DfuBaseService.TYPE_APPLICATION;
		else if (scope == SCOPE_SYSTEM_COMPONENTS)
			fileType = DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER;
		else if (scope == (SCOPE_APPLICATION | SCOPE_SYSTEM_COMPONENTS))
			fileType = DfuBaseService.TYPE_AUTO;
		else throw new UnsupportedOperationException("Unknown scope");
		return this;
	}

	/**
	 * This method sets the size of an MBR (Master Boot Record). It should be used only
	 * when updating a file from a HEX file. If you use BIN or ZIP, value set here will
	 * be ignored.
	 * <p>
	 * The MBR size is important for the HEX parser, which has to cut it from the Soft Device's
	 * HEX before sending it to the DFU target. The MBR can't be updated using DFU, and the
	 * bootloader expects only the Soft Device bytes. Usually, the Soft Device HEX provided
	 * by Nordic contains an MBR at addresses 0x0000 to 0x1000.
	 * 0x1000 is the default size of MBR which will be used.
	 * <p>
	 * If you have a HEX file which address start from 0 and want to send the whole BIN content
	 * from it, you have to set the MBR size to 0, otherwise first 4096 bytes will be cut off.
	 * <p>
	 * The value set here will not be used if the {@link DfuSettingsConstants#SETTINGS_MBR_SIZE}
	 * is set in Shared Preferences.
	 *
	 * @param mbrSize the MBR size in bytes. Defaults to 4096 (0x1000) bytes.
	 * @return the builder
	 * @see DfuSettingsConstants#SETTINGS_MBR_SIZE
	 */
	public DfuServiceInitiator setMbrSize(@IntRange(from = 0) final int mbrSize) {
		this.mbrSize = mbrSize;
		return this;
	}

	/**
	 * Set this flag to true to enable experimental buttonless feature in Secure DFU. When the
	 * experimental Buttonless DFU Service is found on a device, the service will use it to
	 * switch the device to the bootloader mode, connect to it in that mode and proceed with DFU.
	 * <p>
	 * <b>Please, read the information below before setting it to true.</b>
	 * <p>
	 * In the SDK 12.x the Buttonless DFU feature for Secure DFU was experimental.
	 * It is NOT recommended to use it: it was not properly tested, had implementation bugs (e.g.
	 * <a href="https://devzone.nordicsemi.com/question/100609/sdk-12-bootloader-erased-after-programming/">link</a>)
	 * and does not required encryption and therefore may lead to DOS attack (anyone can use it to
	 * switch the device to bootloader mode). However, as there is no other way to trigger
	 * bootloader mode on devices without a button, this DFU Library supports this service, but the
	 * feature must be explicitly enabled here. Be aware, that setting this flag to false will no
	 * protect your devices from this kind of attacks, as an attacker may use another app for that
	 * purpose. To be sure your device is secure remove this experimental service from your device.
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
	 * The Buttonless service has changed in SDK 13 and later. Indications are used instead of
	 * notifications. Also, Buttonless service for bonded devices has been added.
	 * It is recommended to use any of the new services instead.
	 *
	 * @return the builder
	 */
	public DfuServiceInitiator setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(final boolean enable) {
		this.enableUnsafeExperimentalButtonlessDfu = enable;
		return this;
	}

	/**
	 * Sets custom UUIDs for Legacy DFU and Legacy Buttonless DFU. Use this method if your DFU
	 * implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 *
	 * @param dfuServiceUuid      custom Legacy DFU service UUID or null, if default is to be used
	 * @param dfuControlPointUuid custom Legacy DFU Control Point characteristic UUID or null,
	 *                            if default is to be used
	 * @param dfuPacketUuid       custom Legacy DFU Packet characteristic UUID or null, if default is
	 *                            to be used
	 * @param dfuVersionUuid      custom Legacy DFU Version characteristic UUID or null,
	 *                            if default is to be used (SDK 7.0 - 11.0 only, set null for earlier SDKs)
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForLegacyDfu(@Nullable final UUID dfuServiceUuid,
														  @Nullable final UUID dfuControlPointUuid,
														  @Nullable final UUID dfuPacketUuid,
														  @Nullable final UUID dfuVersionUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[4];
		uuids[0] = dfuServiceUuid      != null ? new ParcelUuid(dfuServiceUuid)      : null;
		uuids[1] = dfuControlPointUuid != null ? new ParcelUuid(dfuControlPointUuid) : null;
		uuids[2] = dfuPacketUuid       != null ? new ParcelUuid(dfuPacketUuid)       : null;
		uuids[3] = dfuVersionUuid      != null ? new ParcelUuid(dfuVersionUuid)      : null;
		legacyDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for Secure DFU. Use this method if your DFU implementation uses different
	 * UUID for at least one of the given UUIDs. Parameter set to <code>null</code> will reset
	 * the UUID to the default value.
	 *
	 * @param dfuServiceUuid      custom Secure DFU service UUID or null, if default is to be used
	 * @param dfuControlPointUuid custom Secure DFU Control Point characteristic UUID or null,
	 *                            if default is to be used
	 * @param dfuPacketUuid       custom Secure DFU Packet characteristic UUID or null, if default
	 *                            is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForSecureDfu(@Nullable final UUID dfuServiceUuid,
														  @Nullable final UUID dfuControlPointUuid,
														  @Nullable final UUID dfuPacketUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[3];
		uuids[0] = dfuServiceUuid      != null ? new ParcelUuid(dfuServiceUuid)      : null;
		uuids[1] = dfuControlPointUuid != null ? new ParcelUuid(dfuControlPointUuid) : null;
		uuids[2] = dfuPacketUuid       != null ? new ParcelUuid(dfuPacketUuid)       : null;
		secureDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the experimental Buttonless DFU Service from SDK 12.x. Use this method
	 * if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * <p>
	 * Remember to call {@link #setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(boolean)}
	 * with parameter <code>true</code> if you intent to use this service.
	 *
	 * @param buttonlessDfuServiceUuid      custom Buttonless DFU service UUID or null, if default
	 *                                      is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null,
	 *                                      if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForExperimentalButtonlessDfu(@Nullable final UUID buttonlessDfuServiceUuid,
																		  @Nullable final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		experimentalButtonlessDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the Buttonless DFU Service from SDK 14 (or later).
	 * Use this method if your DFU implementation uses different UUID for at least one of the given
	 * UUIDs. Parameter set to <code>null</code> will reset the UUID to the default value.
	 *
	 * @param buttonlessDfuServiceUuid      custom Buttonless DFU service UUID or null, if default
	 *                                      is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null,
	 *                                      if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForButtonlessDfuWithBondSharing(@Nullable final UUID buttonlessDfuServiceUuid,
																			 @Nullable final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		buttonlessDfuWithBondSharingUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the Buttonless DFU Service from SDK 13. Use this method if your DFU
	 * implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 *
	 * @param buttonlessDfuServiceUuid      custom Buttonless DFU service UUID or null, if default
	 *                                      is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null,
	 *                                      if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForButtonlessDfuWithoutBondSharing(@Nullable final UUID buttonlessDfuServiceUuid,
																				@Nullable final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		buttonlessDfuWithoutBondSharingUuids = uuids;
		return this;
	}

	/**
	 * Sets the URI to the Distribution packet (ZIP) or to a ZIP file matching the deprecated naming
	 * convention.
	 *
	 * @param uri the URI of the file
	 * @return the builder
	 * @see #setZip(String)
	 * @see #setZip(int)
	 */
	public DfuServiceInitiator setZip(@NonNull final Uri uri) {
		return init(uri, null, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the path to the Distribution packet (ZIP) or the a ZIP file matching the deprecated naming
	 * convention.
	 *
	 * @param path path to the file
	 * @return the builder
	 * @see #setZip(Uri)
	 * @see #setZip(int)
	 */
	public DfuServiceInitiator setZip(@NonNull final String path) {
		return init(null, path, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the resource ID of the Distribution packet (ZIP) or the a ZIP file matching the
	 * deprecated naming convention. The file should be in the /res/raw folder.
	 *
	 * @param rawResId file's resource ID
	 * @return the builder
	 * @see #setZip(Uri)
	 * @see #setZip(String)
	 */
	public DfuServiceInitiator setZip(@RawRes final int rawResId) {
		return init(null, null, rawResId, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the URI or path of the ZIP file.
	 * At least one of the parameters must not be null.
	 * If the URI and path are not null the URI will be used.
	 *
	 * @param uri  the URI of the file
	 * @param path the path of the file
	 * @return the builder
	 */
	public DfuServiceInitiator setZip(@Nullable final Uri uri, @Nullable final String path) {
		return init(uri, path, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the URI of the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of
	 * {@link #setInitFile(Uri)} methods.
	 *
	 * @param fileType the file type, a bit field created from:
	 *                 <ul>
	 *                 <li>{@link DfuBaseService#TYPE_APPLICATION} - the Application will be sent</li>
	 *                 <li>{@link DfuBaseService#TYPE_SOFT_DEVICE} - he Soft Device will be sent</li>
	 *                 <li>{@link DfuBaseService#TYPE_BOOTLOADER} - the Bootloader will be sent</li>
	 *                 </ul>
	 * @param uri      the URI of the file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(@FileType final int fileType, @NonNull final Uri uri) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(uri, null, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI of the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of
	 * {@link #setInitFile(String)} methods.
	 *
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param path     path to the file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(@FileType final int fileType, @NonNull final String path) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(null, path, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI or path to the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of
	 * {@link #setInitFile(String)} methods.
	 *
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param uri      the URI of the file
	 * @param path     path to the file
	 * @return the builder
	 * @deprecated The Distribution packet (ZIP) should be used for DFU Bootloader version 0.5 or newer
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(@FileType final int fileType, @Nullable final Uri uri, @Nullable final String path) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(uri, path, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the resource ID pointing the BIN or HEX file containing the new firmware.
	 * The file should be in the /res/raw folder. For DFU Bootloader version 0.5 or newer the init
	 * file must be specified using one of {@link #setInitFile(int)} methods.
	 *
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param rawResId resource ID
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(@FileType final int fileType, @RawRes final int rawResId) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(null, null, rawResId, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI of the Init file. The init file for DFU Bootloader version pre-0.5
	 * (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 *
	 * @param initFileUri the URI of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(@NonNull final Uri initFileUri) {
		return init(initFileUri, null, 0);
	}

	/**
	 * Sets the path to the Init file. The init file for DFU Bootloader version pre-0.5
	 * (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 *
	 * @param initFilePath the path to the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(@Nullable final String initFilePath) {
		return init(null, initFilePath, 0);
	}

	/**
	 * Sets the resource ID of the Init file. The init file for DFU Bootloader version pre-0.5
	 * (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 *
	 * @param initFileResId the resource ID of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(@RawRes final int initFileResId) {
		return init(null, null, initFileResId);
	}

	/**
	 * Sets the URI or path to the Init file. The init file for DFU Bootloader version pre-0.5
	 * (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware. Bootloader version 0.5 or newer
	 * requires the Extended Init Packet. If the URI and path are not null the URI will be used.
	 *
	 * @param initFileUri  the URI of the init file
	 * @param initFilePath the path of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(@Nullable final Uri initFileUri, @Nullable final String initFilePath) {
		return init(initFileUri, initFilePath, 0);
	}

	/**
	 * Starts the DFU service.
	 *
	 * @param context the application context
	 * @param service the class derived from the BaseDfuService
	 */
	public DfuServiceController start(@NonNull final Context context, @NonNull final Class<? extends DfuBaseService> service) {
		if (fileType == -1)
			throw new UnsupportedOperationException("You must specify the firmware file before starting the service");

		final Intent intent = new Intent(context, service);

		intent.putExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(DfuBaseService.EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(DfuBaseService.EXTRA_DISABLE_NOTIFICATION, disableNotification);
		intent.putExtra(DfuBaseService.EXTRA_FOREGROUND_SERVICE, startAsForegroundService);
		intent.putExtra(DfuBaseService.EXTRA_FILE_MIME_TYPE, mimeType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_TYPE, fileType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_URI, fileUri);
		intent.putExtra(DfuBaseService.EXTRA_FILE_PATH, filePath);
		intent.putExtra(DfuBaseService.EXTRA_FILE_RES_ID, fileResId);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_URI, initFileUri);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_PATH, initFilePath);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_RES_ID, initFileResId);
		intent.putExtra(DfuBaseService.EXTRA_KEEP_BOND, keepBond);
		intent.putExtra(DfuBaseService.EXTRA_RESTORE_BOND, restoreBond);
		intent.putExtra(DfuBaseService.EXTRA_FORCE_DFU, forceDfu);
		intent.putExtra(DfuBaseService.EXTRA_DISABLE_RESUME, disableResume);
		intent.putExtra(DfuBaseService.EXTRA_MAX_DFU_ATTEMPTS, numberOfRetries);
		intent.putExtra(DfuBaseService.EXTRA_MBR_SIZE, mbrSize);
		intent.putExtra(DfuBaseService.EXTRA_DATA_OBJECT_DELAY, dataObjectDelay);
		if (mtu > 0)
			intent.putExtra(DfuBaseService.EXTRA_MTU, mtu);
		intent.putExtra(DfuBaseService.EXTRA_CURRENT_MTU, currentMtu);
		intent.putExtra(DfuBaseService.EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU, enableUnsafeExperimentalButtonlessDfu);
		//noinspection StatementWithEmptyBody
		if (packetReceiptNotificationsEnabled != null) {
			intent.putExtra(DfuBaseService.EXTRA_PACKET_RECEIPT_NOTIFICATIONS_ENABLED, packetReceiptNotificationsEnabled);
			intent.putExtra(DfuBaseService.EXTRA_PACKET_RECEIPT_NOTIFICATIONS_VALUE, numberOfPackets);
		} else {
			// For backwards compatibility:
			// If the setPacketsReceiptNotificationsEnabled(boolean) has not been called, the PRN state and value are taken from
			// SharedPreferences the way they were read in DFU Library in 1.0.3 and before, or set to default values.
			// Default values: PRNs enabled on Android 4.3 - 5.1 and disabled starting from Android 6.0. Default PRN value is 12.
		}
		if (legacyDfuUuids != null)
			intent.putExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_LEGACY_DFU, legacyDfuUuids);
		if (secureDfuUuids != null)
			intent.putExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_SECURE_DFU, secureDfuUuids);
		if (experimentalButtonlessDfuUuids != null)
			intent.putExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_EXPERIMENTAL_BUTTONLESS_DFU, experimentalButtonlessDfuUuids);
		if (buttonlessDfuWithoutBondSharingUuids != null)
			intent.putExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITHOUT_BOND_SHARING, buttonlessDfuWithoutBondSharingUuids);
		if (buttonlessDfuWithBondSharingUuids != null)
			intent.putExtra(DfuBaseService.EXTRA_CUSTOM_UUIDS_FOR_BUTTONLESS_DFU_WITH_BOND_SHARING, buttonlessDfuWithBondSharingUuids);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && startAsForegroundService) {
			// On Android Oreo and above the service must be started as a foreground service to make it accessible from
			// a killed application.
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
		return new DfuServiceController(context);
	}

	private DfuServiceInitiator init(@Nullable final Uri initFileUri,
									 @Nullable final String initFilePath,
									 @RawRes final int initFileResId) {
		if (DfuBaseService.MIME_TYPE_ZIP.equals(mimeType))
			throw new InvalidParameterException("Init file must be located inside the ZIP");

		this.initFileUri = initFileUri;
		this.initFilePath = initFilePath;
		this.initFileResId = initFileResId;
		return this;
	}

	private DfuServiceInitiator init(@Nullable final Uri fileUri,
									 @Nullable final String filePath,
									 @RawRes final int fileResId, @FileType final int fileType,
									 @NonNull final String mimeType) {
		this.fileUri = fileUri;
		this.filePath = filePath;
		this.fileResId = fileResId;
		this.fileType = fileType;
		this.mimeType = mimeType;

		// If the MIME TYPE implies it's a ZIP file then the init file must be included in the file.
		if (DfuBaseService.MIME_TYPE_ZIP.equals(mimeType)) {
			this.initFileUri = null;
			this.initFilePath = null;
			this.initFileResId = 0;
		}
		return this;
	}

	@RequiresApi(api = Build.VERSION_CODES.O)
	public static void createDfuNotificationChannel(@NonNull final Context context) {
		final NotificationChannel channel =
				new NotificationChannel(DfuBaseService.NOTIFICATION_CHANNEL_DFU, context.getString(R.string.dfu_channel_name), NotificationManager.IMPORTANCE_LOW);
		channel.setDescription(context.getString(R.string.dfu_channel_description));
		channel.setShowBadge(false);
		channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

		final NotificationManager notificationManager =
				(NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (notificationManager != null) {
			notificationManager.createNotificationChannel(channel);
		}
	}
}