/**
 * **********************************************************************************************************************************************
 * Copyright (c) 2015, Nordic Semiconductor
 * All rights reserved.
 * <p/>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * <p/>
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * <p/>
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution.
 * <p/>
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 * <p/>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * **********************************************************************************************************************************************
 */

package no.nordicsemi.android.dfu;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelUuid;
import android.os.Parcelable;

import java.security.InvalidParameterException;
import java.util.UUID;

/**
 * Starting the DfuService service requires a knowledge of some EXTRA_* constants used to pass parameters to the service.
 * The DfuServiceInitiator class may be used to make this process easier. It provides simple API that covers all low lever operations.
 */
public class DfuServiceInitiator {
	public static final int DEFAULT_PRN_VALUE = 12;

	private final String deviceAddress;
	private String deviceName;

	private boolean disableNotification;

	private Uri fileUri;
	private String filePath;
	private int fileResId;

	private Uri initFileUri;
	private String initFilePath;
	private int initFileResId;

	private String mimeType;
	private int fileType = -1;

	private boolean keepBond;
	private boolean forceDfu = false;
	private boolean enableUnsafeExperimentalButtonlessDfu = false;

	private Boolean packetReceiptNotificationsEnabled;
	private int numberOfPackets = 12;

	private Parcelable[] legacyDfuUuids;
	private Parcelable[] secureDfuUuids;
	private Parcelable[] experimentalButtonlessDfuUuids;
	private Parcelable[] buttonlessDfuWithoutBondSharingUuids;
	private Parcelable[] buttonlessDfuWithBondSharingUuids;

	/**
	 * Creates the builder. Use setZip(...), or setBinOrHex(...) methods to specify the file you want to upload.
	 * In the latter case an init file may also be set using the setInitFile(...) method. Init files are required by DFU Bootloader version 0.5 or newer (SDK 7.0.0+).
	 * @param deviceAddress the target device device address
	 */
	public DfuServiceInitiator(final String deviceAddress) {
		this.deviceAddress = deviceAddress;
	}

	/**
	 * Sets the device name. The device name is not required. It's written in the notification during the DFU process.
	 * If not set the {@link no.nordicsemi.android.dfu.R.string#dfu_unknown_name R.string.dfu_unknown_name} value will be used.
	 * @param name the device name (optional)
	 * @return the builder
	 */
	public DfuServiceInitiator setDeviceName(final String name) {
		this.deviceName = name;
		return this;
	}

	/**
	 * Sets whether the progress notification in the status bar should be disabled.
	 * Defaults to false.
	 * @param disableNotification whether to disable the notification
	 * @return the builder
	 */
	public DfuServiceInitiator setDisableNotification(final boolean disableNotification) {
		this.disableNotification = disableNotification;
		return this;
	}

	/**
	 * Sets whether the bond information should be preserver after flashing new application. This feature requires DFU Bootloader version 0.6 or newer (SDK 8.0.0+).
	 * Please see the {@link DfuBaseService#EXTRA_KEEP_BOND} for more information regarding requirements. Remember that currently updating the Soft Device will remove the bond information.
	 * @param keepBond whether the bond information should be preserved in the new application.
	 * @return the builder
	 */
	public DfuServiceInitiator setKeepBond(final boolean keepBond) {
		this.keepBond = keepBond;
		return this;
	}

	/**
	 * Enables or disables the Packet Receipt Notification (PRN) procedure.
	 * <p>By default the PRNs are disabled on devices with Android Marshmallow or newer and enabled on older ones.</p>
	 * @param enabled true to enabled PRNs, false to disable
	 * @return the builder
	 * @see DfuSettingsConstants#SETTINGS_PACKET_RECEIPT_NOTIFICATION_ENABLED
	 */
	public DfuServiceInitiator setPacketsReceiptNotificationsEnabled(final boolean enabled) {
		this.packetReceiptNotificationsEnabled = enabled;
		return this;
	}

	/**
	 * If Packet Receipt Notification procedure is enabled, this method sets number of packets to be sent before
	 * receiving a PRN. A PRN is used to synchronize the transmitter and receiver.
	 * @param number number of packets to be sent before receiving a PRN. Defaulted when set to 0.
	 * @return the builder
	 * @see #setPacketsReceiptNotificationsEnabled(boolean)
	 * @see DfuSettingsConstants#SETTINGS_NUMBER_OF_PACKETS
	 */
	public DfuServiceInitiator setPacketsReceiptNotificationsValue(final int number) {
		this.numberOfPackets = number > 0 ? number : DEFAULT_PRN_VALUE;
		return this;
	}

	/**
	 * Setting force DFU to true will prevent from jumping to the DFU Bootloader
	 * mode in case there is no DFU Version characteristic (Legacy DFU only!). Use it if the DFU operation can be handled by your
	 * device running in the application mode.
	 *
	 * <p>If the DFU Version characteristic exists, the
	 * information whether to begin DFU operation, or jump to bootloader, is taken from that
	 * characteristic's value. The value returned equal to 0x0100 (read as: minor=1, major=0, or version 0.1)
	 * means that the device is in the application mode and buttonless jump to DFU Bootloader is supported.</p>
	 *
	 * <p>However, if there is no DFU Version characteristic, a device
	 * may support only Application update (version from SDK 4.3.0), may support Soft Device, Bootloader
	 * and Application update but without buttonless jump to bootloader (SDK 6.0.0) or with
	 * buttonless jump (SDK 6.1.0).</p>
	 *
	 * <p>In the last case, the DFU Library determines whether the device is in application mode or in DFU Bootloader mode
	 * by counting number of services: if no DFU Service found - device is in app mode and does not support
	 * buttonless jump, if the DFU Service is the only service found (except General Access and General Attribute
	 * services) - it assumes it is in DFU Bootloader mode and may start DFU immediately, if there is
	 * at least one service except DFU Service - the device is in application mode and supports buttonless
	 * jump. In the last case, if you want to perform DFU operation without jumping - call the {@link #setForceDfu(boolean)}
	 * method with parameter equal to true.</p>
	 *
	 * <p>This method is ignored in Secure DFU.</p>
	 * @param force true to ensure the DFU will start if there is no DFU Version characteristic (Legacy DFU only)
	 * @return the builder
	 * @see DfuSettingsConstants#SETTINGS_ASSUME_DFU_NODE
	 */
	public DfuServiceInitiator setForceDfu(final boolean force) {
		this.forceDfu = force;
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
	 * It is NOT recommended to use it: it was not properly tested, had implementation bugs
	 * (e.g. <a href="https://devzone.nordicsemi.com/question/100609/sdk-12-bootloader-erased-after-programming/">link</a>) and
	 * does not required encryption and therefore may lead to DOS attack (anyone can use it to switch the device
	 * to bootloader mode). However, as there is no other way to trigger bootloader mode on devices
	 * without a button, this DFU Library supports this service, but the feature must be explicitly enabled here.
	 * Be aware, that setting this flag to false will no protect your devices from this kind of attacks, as
	 * an attacker may use another app for that purpose. To be sure your device is secure remove this
	 * experimental service from your device.
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
	 * In SDK 13 this issue will be fixed by a proper implementation (bonding required,
	 * passing bond information to the bootloader, encryption, well tested). It is recommended to use this
	 * new service when SDK 13 (or later) is out. TODO fix the docs when SDK 13 is out.
	 */
	public DfuServiceInitiator setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(final boolean enable) {
		this.enableUnsafeExperimentalButtonlessDfu = enable;
		return this;
	}

	/**
	 * Sets custom UUIDs for Legacy DFU and Legacy Buttonless DFU. Use this method if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * @param dfuServiceUuid custom Legacy DFU service UUID or null, if default is to be used
	 * @param dfuControlPointUuid custom Legacy DFU Control Point characteristic UUID or null, if default is to be used
	 * @param dfuPacketUuid custom Legacy DFU Packet characteristic UUID or null, if default is to be used
	 * @param dfuVersionUuid custom Legacy DFU Version characteristic UUID or null, if default is to be used (SDK 7.0 - 11.0 only, set null for earlier SDKs)
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForLegacyDfu(final UUID dfuServiceUuid, final UUID dfuControlPointUuid, final UUID dfuPacketUuid, final UUID dfuVersionUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[4];
		uuids[0] = dfuServiceUuid      != null ? new ParcelUuid(dfuServiceUuid)      : null;
		uuids[1] = dfuControlPointUuid != null ? new ParcelUuid(dfuControlPointUuid) : null;
		uuids[2] = dfuPacketUuid       != null ? new ParcelUuid(dfuPacketUuid)       : null;
		uuids[3] = dfuVersionUuid      != null ? new ParcelUuid(dfuVersionUuid)      : null;
		legacyDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for Secure DFU. Use this method if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * @param dfuServiceUuid custom Secure DFU service UUID or null, if default is to be used
	 * @param dfuControlPointUuid custom Secure DFU Control Point characteristic UUID or null, if default is to be used
	 * @param dfuPacketUuid custom Secure DFU Packet characteristic UUID or null, if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForSecureDfu(final UUID dfuServiceUuid, final UUID dfuControlPointUuid, final UUID dfuPacketUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[3];
		uuids[0] = dfuServiceUuid      != null ? new ParcelUuid(dfuServiceUuid)      : null;
		uuids[1] = dfuControlPointUuid != null ? new ParcelUuid(dfuControlPointUuid) : null;
		uuids[2] = dfuPacketUuid       != null ? new ParcelUuid(dfuPacketUuid)       : null;
		secureDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the experimental Buttonless DFU Service from SDK 12.x. Use this method if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * <p>Remember to call {@link #setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(boolean)} with parameter <code>true</code> if you intent to use this service.</p>
	 * @param buttonlessDfuServiceUuid custom Buttonless DFU service UUID or null, if default is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null, if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForExperimentalButtonlessDfu(final UUID buttonlessDfuServiceUuid, final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		experimentalButtonlessDfuUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the Buttonless DFU Service from SDK 14 (or later). Use this method if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * @param buttonlessDfuServiceUuid custom Buttonless DFU service UUID or null, if default is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null, if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForButtonlessDfuWithBondSharing(final UUID buttonlessDfuServiceUuid, final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		buttonlessDfuWithBondSharingUuids = uuids;
		return this;
	}

	/**
	 * Sets custom UUIDs for the Buttonless DFU Service from SDK 13. Use this method if your DFU implementation uses different UUID for at least one of the given UUIDs.
	 * Parameter set to <code>null</code> will reset the UUID to the default value.
	 * @param buttonlessDfuServiceUuid custom Buttonless DFU service UUID or null, if default is to be used
	 * @param buttonlessDfuControlPointUuid custom Buttonless DFU characteristic UUID or null, if default is to be used
	 * @return the builder
	 */
	public DfuServiceInitiator setCustomUuidsForButtonlessDfuWithoutBondSharing(final UUID buttonlessDfuServiceUuid, final UUID buttonlessDfuControlPointUuid) {
		final ParcelUuid[] uuids = new ParcelUuid[2];
		uuids[0] = buttonlessDfuServiceUuid      != null ? new ParcelUuid(buttonlessDfuServiceUuid)      : null;
		uuids[1] = buttonlessDfuControlPointUuid != null ? new ParcelUuid(buttonlessDfuControlPointUuid) : null;
		buttonlessDfuWithoutBondSharingUuids = uuids;
		return this;
	}

	/**
	 * Sets the URI to the Distribution packet (ZIP) or to a ZIP file matching the deprecated naming convention.
	 * @param uri the URI of the file
	 * @return the builder
	 * @see #setZip(String)
	 * @see #setZip(int)
	 */
	public DfuServiceInitiator setZip(final Uri uri) {
		return init(uri, null, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the path to the Distribution packet (ZIP) or the a ZIP file matching the deprecated naming convention.
	 * @param path path to the file
	 * @return the builder
	 * @see #setZip(Uri)
	 * @see #setZip(int)
	 */
	public DfuServiceInitiator setZip(final String path) {
		return init(null, path, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the resource ID of the Distribution packet (ZIP) or the a ZIP file matching the deprecated naming convention. The file should be in the /res/raw folder.
	 * @param rawResId file's resource ID
	 * @return the builder
	 * @see #setZip(Uri)
	 * @see #setZip(String)
	 */
	public DfuServiceInitiator setZip(final int rawResId) {
		return init(null, null, rawResId, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the URI or path of the ZIP file. If the URI and path are not null the URI will be used.
	 * @param uri the URI of the file
	 * @param path the path of the file
	 * @return the builder
	 */
	public DfuServiceInitiator setZip(final Uri uri, final String path) {
		return init(uri, path, 0, DfuBaseService.TYPE_AUTO, DfuBaseService.MIME_TYPE_ZIP);
	}

	/**
	 * Sets the URI of the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of {@link #setInitFile(Uri)} methods.
	 * @param fileType the file type, a bit field created from:
	 *  	<ul>
	 * 		    <li>{@link DfuBaseService#TYPE_APPLICATION} - the Application will be sent</li>
	 * 		    <li>{@link DfuBaseService#TYPE_SOFT_DEVICE} - he Soft Device will be sent</li>
	 * 		    <li>{@link DfuBaseService#TYPE_BOOTLOADER} - the Bootloader will be sent</li>
	 * 		</ul>
	 * @param uri the URI of the file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(final int fileType, final Uri uri) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(uri, null, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI of the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of {@link #setInitFile(String)} methods.
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param path path to the file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(final int fileType, final String path) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(null, path, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI or path to the BIN or HEX file containing the new firmware.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of {@link #setInitFile(String)} methods.
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param uri the URI of the file
	 * @param path path to the file
	 * @return the builder
	 * @deprecated The Distribution packet (ZIP) should be used for DFU Bootloader version 0.5 or newer
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(final int fileType, final Uri uri, final String path) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(uri, path, 0, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the resource ID pointing the BIN or HEX file containing the new firmware. The file should be in the /res/raw folder.
	 * For DFU Bootloader version 0.5 or newer the init file must be specified using one of {@link #setInitFile(int)} methods.
	 * @param fileType see {@link #setBinOrHex(int, Uri)} for details
	 * @param rawResId resource ID
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setBinOrHex(final int fileType, final int rawResId) {
		if (fileType == DfuBaseService.TYPE_AUTO)
			throw new UnsupportedOperationException("You must specify the file type");
		return init(null, null, rawResId, fileType, DfuBaseService.MIME_TYPE_OCTET_STREAM);
	}

	/**
	 * Sets the URI of the Init file. The init file for DFU Bootloader version pre-0.5 (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 * @param initFileUri the URI of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(final Uri initFileUri) {
		return init(initFileUri, null, 0);
	}

	/**
	 * Sets the path to the Init file. The init file for DFU Bootloader version pre-0.5 (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 * @param initFilePath the path to the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(final String initFilePath) {
		return init(null, initFilePath, 0);
	}

	/**
	 * Sets the resource ID of the Init file. The init file for DFU Bootloader version pre-0.5 (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet.
	 * @param initFileResId the resource ID of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(final int initFileResId) {
		return init(null, null, initFileResId);
	}

	/**
	 * Sets the URI or path to the Init file. The init file for DFU Bootloader version pre-0.5 (SDK 4.3, 6.0, 6.1) contains only the CRC-16 of the firmware.
	 * Bootloader version 0.5 or newer requires the Extended Init Packet. If the URI and path are not null the URI will be used.
	 * @param initFileUri the URI of the init file
	 * @param initFilePath the path of the init file
	 * @return the builder
	 */
	@Deprecated
	public DfuServiceInitiator setInitFile(final Uri initFileUri, final String initFilePath) {
		return init(initFileUri, initFilePath, 0);
	}

	/**
	 * Starts the DFU service.
	 * @param context the application context
	 * @param service the class derived from the BaseDfuService
	 */
	public DfuServiceController start(final Context context, final Class<? extends DfuBaseService> service) {
		if (fileType == -1)
			throw new UnsupportedOperationException("You must specify the firmware file before starting the service");

		final Intent intent = new Intent(context, service);

		intent.putExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(DfuBaseService.EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(DfuBaseService.EXTRA_DISABLE_NOTIFICATION, disableNotification);
		intent.putExtra(DfuBaseService.EXTRA_FILE_MIME_TYPE, mimeType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_TYPE, fileType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_URI, fileUri);
		intent.putExtra(DfuBaseService.EXTRA_FILE_PATH, filePath);
		intent.putExtra(DfuBaseService.EXTRA_FILE_RES_ID, fileResId);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_URI, initFileUri);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_PATH, initFilePath);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_RES_ID, initFileResId);
		intent.putExtra(DfuBaseService.EXTRA_KEEP_BOND, keepBond);
		intent.putExtra(DfuBaseService.EXTRA_FORCE_DFU, forceDfu);
		intent.putExtra(DfuBaseService.EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU, enableUnsafeExperimentalButtonlessDfu);
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

		context.startService(intent);
		return new DfuServiceController(context);
	}

	private DfuServiceInitiator init(final Uri initFileUri, final String initFilePath, final int initFileResId) {
		if (DfuBaseService.MIME_TYPE_ZIP.equals(mimeType))
			throw new InvalidParameterException("Init file must be located inside the ZIP");

		this.initFileUri = initFileUri;
		this.initFilePath = initFilePath;
		this.initFileResId = initFileResId;
		return this;
	}

	private DfuServiceInitiator init(final Uri fileUri, final String filePath, final int fileResId, final int fileType, final String mimeType) {
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
}