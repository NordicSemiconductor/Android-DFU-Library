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

import java.security.InvalidParameterException;

/**
 * Starting the DfuService service requires a knowledge of some EXTRA_* constants used to pass parameters to the service.
 * The DfuServiceInitiator class may be used to make this process easier. It provides simple API that covers all low lever operations.
 */
public class DfuServiceInitiator {
	private final String deviceAddress;
	private String deviceName;

	private Uri fileUri;
	private String filePath;
	private int fileResId;

	private Uri initFileUri;
	private String initFilePath;
	private int initFileResId;

	private String mimeType;
	private int fileType = -1;

	private boolean keepBond;

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
	public void start(final Context context, final Class<? extends DfuBaseService> service) {
		if (fileType == -1)
			throw new UnsupportedOperationException("You must specify the firmware file before starting the service");

		final Intent intent = new Intent(context, service);

		intent.putExtra(DfuBaseService.EXTRA_DEVICE_ADDRESS, deviceAddress);
		intent.putExtra(DfuBaseService.EXTRA_DEVICE_NAME, deviceName);
		intent.putExtra(DfuBaseService.EXTRA_FILE_MIME_TYPE, mimeType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_TYPE, fileType);
		intent.putExtra(DfuBaseService.EXTRA_FILE_URI, fileUri);
		intent.putExtra(DfuBaseService.EXTRA_FILE_PATH, filePath);
		intent.putExtra(DfuBaseService.EXTRA_FILE_RES_ID, fileResId);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_URI, initFileUri);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_PATH, initFilePath);
		intent.putExtra(DfuBaseService.EXTRA_INIT_FILE_RES_ID, initFileResId);
		intent.putExtra(DfuBaseService.EXTRA_KEEP_BOND, keepBond);

		context.startService(intent);
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