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

package no.nordicsemi.android.dfu.internal;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.internal.manifest.FileInfo;
import no.nordicsemi.android.dfu.internal.manifest.Manifest;
import no.nordicsemi.android.dfu.internal.manifest.ManifestFile;
import no.nordicsemi.android.dfu.internal.manifest.SoftDeviceBootloaderFileInfo;

/**
 * <p>Reads the firmware files from the a ZIP file. The ZIP file must be either created using the <b>nrf utility</b> tool, available together with Master Control Panel v3.8.0+,
 * or follow the backward compatibility syntax: must contain only files with names: application.hex/bin, softdevice.hex/dat or bootloader.hex/bin, optionally also application.dat
 * and/or system.dat with init packets.</p>
 * <p>The ArchiveInputStream will read only files with types specified by <b>types</b> parameter of the constructor.</p>
 */
public class ArchiveInputStream extends ZipInputStream {
	/** The name of the manifest file is fixed. */
	private static final String MANIFEST = "manifest.json";
	// Those file names are for backwards compatibility mode
	private static final String SOFTDEVICE_HEX = "softdevice.hex";
	private static final String SOFTDEVICE_BIN = "softdevice.bin";
	private static final String BOOTLOADER_HEX = "bootloader.hex";
	private static final String BOOTLOADER_BIN = "bootloader.bin";
	private static final String APPLICATION_HEX = "application.hex";
	private static final String APPLICATION_BIN = "application.bin";
	private static final String SYSTEM_INIT = "system.dat";
	private static final String APPLICATION_INIT = "application.dat";

	/** Contains bytes arrays with BIN files. HEX files are converted to BIN before being added to this map. */
	private Map<String, byte[]> entries;
	private Manifest manifest;

	private byte[] applicationBytes;
	private byte[] softDeviceBytes;
	private byte[] bootloaderBytes;
	private byte[] softDeviceAndBootloaderBytes;
	private byte[] systemInitBytes;
	private byte[] applicationInitBytes;
	private byte[] currentSource;
	private int bytesReadFromCurrentSource;
	private int softDeviceSize;
	private int bootloaderSize;
	private int applicationSize;
	private int bytesRead;

	/**
	 * <p>
	 * The ArchiveInputStream read HEX or BIN files from the Zip stream. It may skip some of them, depending on the value of the types parameter.
	 * This is useful if the DFU service wants to send the Soft Device and Bootloader only, and then the Application in the following connection, despite
	 * the ZIP file contains all 3 HEX/BIN files.
	 * When types is equal to {@link DfuBaseService#TYPE_AUTO} all present files are read.
	 * </p>
	 * <p>Use bit combination of the following types:</p>
	 * <ul>
	 * <li>{@link DfuBaseService#TYPE_SOFT_DEVICE}</li>
	 * <li>{@link DfuBaseService#TYPE_BOOTLOADER}</li>
	 * <li>{@link DfuBaseService#TYPE_APPLICATION}</li>
	 * <li>{@link DfuBaseService#TYPE_AUTO}</li>
	 * </ul>
	 *
	 * @param stream
	 *            the Zip Input Stream
	 * @param mbrSize
	 *            The size of the MRB segment (Master Boot Record) on the device. The parser will cut data from addresses below that number from all HEX files.
	 * @param types
	 *            File types that are to be read from the ZIP. Use {@link DfuBaseService#TYPE_APPLICATION} etc.
	 * @throws java.io.IOException
	 */
	public ArchiveInputStream(final InputStream stream, final int mbrSize, final int types) throws IOException {
		super(stream);

		this.entries = new HashMap<>();
		this.bytesRead = 0;
		this.bytesReadFromCurrentSource = 0;

		try {
			/*
			 * This method reads all entries from the ZIP file and puts them to entries map.
			 * The 'manifest.json' file, if exists, is converted to the manifestData String.
			 */
			parseZip(mbrSize);

			/*
			 * Let's read and parse the 'manifest.json' file.
			 */
			if (manifest != null) {
				boolean valid = false;

				// Read the application
				if (manifest.getApplicationInfo() != null && (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_APPLICATION) > 0)) {
					final FileInfo application = manifest.getApplicationInfo();
					applicationBytes = entries.get(application.getBinFileName());
					applicationInitBytes = entries.get(application.getDatFileName());

					if (applicationBytes == null)
						throw new IOException("Application file " + application.getBinFileName() + " not found.");
					applicationSize = applicationBytes.length;
					currentSource = applicationBytes;
					valid = true;
				}

				// Read the Bootloader
				if (manifest.getBootloaderInfo() != null && (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_BOOTLOADER) > 0)) {
					if (systemInitBytes != null)
						throw new IOException("Manifest: softdevice and bootloader specified. Use softdevice_bootloader instead.");

					final FileInfo bootloader = manifest.getBootloaderInfo();
					bootloaderBytes = entries.get(bootloader.getBinFileName());
					systemInitBytes = entries.get(bootloader.getDatFileName());

					if (bootloaderBytes == null)
						throw new IOException("Bootloader file " + bootloader.getBinFileName() + " not found.");
					bootloaderSize = bootloaderBytes.length;
					currentSource = bootloaderBytes;
					valid = true;
				}

				// Read the Soft Device
				if (manifest.getSoftdeviceInfo() != null && (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_SOFT_DEVICE) > 0)) {
					final FileInfo softdevice = manifest.getSoftdeviceInfo();
					softDeviceBytes = entries.get(softdevice.getBinFileName());
					systemInitBytes = entries.get(softdevice.getDatFileName());

					if (softDeviceBytes == null)
						throw new IOException("SoftDevice file " + softdevice.getBinFileName() + " not found.");
					softDeviceSize = softDeviceBytes.length;
					currentSource = softDeviceBytes;
					valid = true;
				}

				// Read the combined Soft Device and Bootloader
				if (manifest.getSoftdeviceBootloaderInfo() != null && (types == DfuBaseService.TYPE_AUTO ||
						((types & DfuBaseService.TYPE_SOFT_DEVICE) > 0) && (types & DfuBaseService.TYPE_BOOTLOADER) > 0)) {
					if (systemInitBytes != null)
						throw new IOException("Manifest: The softdevice_bootloader may not be used together with softdevice or bootloader.");

					final SoftDeviceBootloaderFileInfo system = manifest.getSoftdeviceBootloaderInfo();
					softDeviceAndBootloaderBytes = entries.get(system.getBinFileName());
					systemInitBytes = entries.get(system.getDatFileName());

					if (softDeviceAndBootloaderBytes == null)
						throw new IOException("File " + system.getBinFileName() + " not found.");
					softDeviceSize = system.getSoftdeviceSize();
					bootloaderSize = system.getBootloaderSize();
					currentSource = softDeviceAndBootloaderBytes;
					valid = true;
				}

				if (!valid) {
					throw new IOException("Manifest file must specify at least one file.");
				}
			} else {
				/*
				 * Compatibility mode. The 'manifest.json' file does not exist.
				 *
				 * In that case the ZIP file must contain one or more of the following files:
				 *
				 * - application.hex/dat
				 *     + application.dat
				 * - softdevice.hex/dat
				 * - bootloader.hex/dat
				 *     + system.dat
				 */
				boolean valid = false;
				// Search for the application
				if (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_APPLICATION) > 0) {
					applicationBytes = entries.get(APPLICATION_HEX); // the entry bytes has already been converted to BIN, just the name remained.
					if (applicationBytes == null)
						applicationBytes = entries.get(APPLICATION_BIN);
					if (applicationBytes != null) {
						applicationSize = applicationBytes.length;
						applicationInitBytes = entries.get(APPLICATION_INIT);
						currentSource = applicationBytes;
						valid = true;
					}
				}

				// Search for theBootloader
				if (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_BOOTLOADER) > 0) {
					bootloaderBytes = entries.get(BOOTLOADER_HEX); // the entry bytes has already been converted to BIN, just the name remained.
					if (bootloaderBytes == null)
						bootloaderBytes = entries.get(BOOTLOADER_BIN);
					if (bootloaderBytes != null) {
						bootloaderSize = bootloaderBytes.length;
						systemInitBytes = entries.get(SYSTEM_INIT);
						currentSource = bootloaderBytes;
						valid = true;
					}
				}

				// Search for the Soft Device
				if (types == DfuBaseService.TYPE_AUTO || (types & DfuBaseService.TYPE_SOFT_DEVICE) > 0) {
					softDeviceBytes = entries.get(SOFTDEVICE_HEX); // the entry bytes has already been converted to BIN, just the name remained.
					if (softDeviceBytes == null)
						softDeviceBytes = entries.get(SOFTDEVICE_BIN);
					if (softDeviceBytes != null) {
						softDeviceSize = softDeviceBytes.length;
						systemInitBytes = entries.get(SYSTEM_INIT);
						currentSource = softDeviceBytes;
						valid = true;
					}
				}

				if (!valid) {
					throw new IOException("The ZIP file must contain an Application, a Soft Device and/or a Bootloader.");
				}
			}
		} finally {
			super.close();
		}
	}

	/**
	 * Reads all files into byte arrays.
	 * Here we don't know whether the ZIP file is valid.
	 *
	 * The ZIP file is valid when contains a 'manifest.json' file and all BIN and DAT files that are specified in the manifest.
	 *
	 * For backwards compatibility ArchiveInputStream supports also ZIP archives without 'manifest.json' file
	 * but than it MUST include at least one of the following files: softdevice.bin/hex, bootloader.bin/hex, application.bin/hex.
	 * To support the init packet such ZIP file should contain also application.dat and/or system.dat (with the CRC16 of a SD, BL or SD+BL together).
	 */
	private void parseZip(int mbrSize) throws IOException {
		final byte[] buffer = new byte[1024];
		String manifestData = null;

		ZipEntry ze;
		while ((ze = getNextEntry()) != null) {
			final String filename = ze.getName();

			// Read file content to byte array
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int count;
			while ((count = super.read(buffer)) != -1) {
				baos.write(buffer, 0, count);
			}
			byte[] source = baos.toByteArray();

			// In case of HEX file convert it to BIN
			if (filename.toLowerCase(Locale.US).endsWith("hex")) {
				final HexInputStream is = new HexInputStream(source, mbrSize);
				source = new byte[is.available()];
				is.read(source);
				is.close();
			}

			// Save the file content either as a manifest data or by adding it to entries
			if (MANIFEST.equals(filename))
				manifestData = new String(source, "UTF-8");
			else
				entries.put(filename, source);
		}

		if (manifestData != null) {
			final ManifestFile manifestFile = new Gson().fromJson(manifestData, ManifestFile.class);
			manifest = manifestFile.getManifest();
		}
	}

	@Override
	public void close() throws IOException {
		softDeviceBytes = null;
		bootloaderBytes = null;
		softDeviceBytes = null;
		softDeviceAndBootloaderBytes = null;
		softDeviceSize = bootloaderSize = applicationSize = 0;
		currentSource = null;
		bytesRead = bytesReadFromCurrentSource = 0;
		super.close();
	}

	@Override
	public int read(final byte[] buffer) throws IOException {
		int maxSize = currentSource.length - bytesReadFromCurrentSource;
		int size = buffer.length <= maxSize ? buffer.length : maxSize;
		System.arraycopy(currentSource, bytesReadFromCurrentSource, buffer, 0, size);
		bytesReadFromCurrentSource += size;
		if (buffer.length > size) {
			if (startNextFile() == null) {
				bytesRead += size;
				return size;
			}

			maxSize = currentSource.length;
			final int nextSize = buffer.length - size <= maxSize ? buffer.length - size : maxSize;
			System.arraycopy(currentSource, 0, buffer, size, nextSize);
			bytesReadFromCurrentSource += nextSize;
			size += nextSize;
		}
		bytesRead += size;
		return size;
	}

	/**
	 * Returns the manifest object if it was specified in the ZIP file.
	 * @return the manifest object
	 */
	public Manifest getManifest() {
		return manifest;
	}

	/**
	 * Returns the content type based on the content of the ZIP file. The content type may be truncated using {@link #setContentType(int)}.
	 * 
	 * @return a bit field of {@link DfuBaseService#TYPE_SOFT_DEVICE TYPE_SOFT_DEVICE}, {@link DfuBaseService#TYPE_BOOTLOADER TYPE_BOOTLOADER} and {@link DfuBaseService#TYPE_APPLICATION
	 *         TYPE_APPLICATION}
	 */
	public int getContentType() {
		byte b = 0;
		if (softDeviceSize > 0)
			b |= DfuBaseService.TYPE_SOFT_DEVICE;
		if (bootloaderSize > 0)
			b |= DfuBaseService.TYPE_BOOTLOADER;
		if (applicationSize > 0)
			b |= DfuBaseService.TYPE_APPLICATION;
		return b;
	}

	/**
	 * Truncates the current content type. May be used to hide some files, f.e. to send Soft Device and Bootloader without Application or only the Application.
	 * 
	 * @param type
	 *            the new type
	 * @return the final type after truncating
	 */
	public int setContentType(final int type) {
		if (bytesRead > 0)
			throw new UnsupportedOperationException("Content type must not be change after reading content");

		final int t = getContentType() & type;

		if ((t & DfuBaseService.TYPE_SOFT_DEVICE) == 0) {
			softDeviceBytes = null;
			if (softDeviceAndBootloaderBytes != null) {
				softDeviceAndBootloaderBytes = null;
				bootloaderSize = 0;
			}
			softDeviceSize = 0;
		}
		if ((t & DfuBaseService.TYPE_BOOTLOADER) == 0) {
			bootloaderBytes = null;
			if (softDeviceAndBootloaderBytes != null) {
				softDeviceAndBootloaderBytes = null;
				softDeviceSize = 0;
			}
			bootloaderSize = 0;
		}
		if ((t & DfuBaseService.TYPE_APPLICATION) == 0) {
			applicationBytes = null;
			applicationSize = 0;
		}
		return t;
	}

	/**
	 * Sets the currentSource to the new file or to <code>null</code> if the last file has been transmitted.
	 * 
	 * @return the new source, the same as {@link #currentSource}
	 */
	private byte[] startNextFile() {
		byte[] ret;
		if (currentSource == softDeviceBytes && bootloaderBytes != null) {
			ret = currentSource = bootloaderBytes;
		} else if (currentSource != applicationBytes && applicationBytes != null) {
			ret = currentSource = applicationBytes;
		} else {
			ret = currentSource = null;
		}
		bytesReadFromCurrentSource = 0;
		return ret;
	}

	@Override
	/**
	 * Returns the number of bytes that has not been read yet. This value includes only firmwares matching the content type set by the construcotor or the {@link #setContentType(int)} method.
	 */
	public int available() {
		return softDeviceSize + bootloaderSize + applicationSize - bytesRead;
	}

	/**
	 * Returns the total size of the SoftDevice firmware. In case the firmware was given as a HEX, this method returns the size of the BIN content of the file.
	 * @return the size of the SoftDevice firmware (BIN part)
	 */
	public int softDeviceImageSize() {
		return softDeviceSize;
	}

	/**
	 * Returns the total size of the Bootloader firmware. In case the firmware was given as a HEX, this method returns the size of the BIN content of the file.
	 * @return the size of the Bootloader firmware (BIN part)
	 */
	public int bootloaderImageSize() {
		return bootloaderSize;
	}

	/**
	 * Returns the total size of the Application firmware. In case the firmware was given as a HEX, this method returns the size of the BIN content of the file.
	 * @return the size of the Application firmware (BIN part)
	 */
	public int applicationImageSize() {
		return applicationSize;
	}

	/**
	 * Returns the content of the init file for SoftDevice and/or Bootloader. When both SoftDevice and Bootloader are present in the ZIP file (as two files using the compatibility mode
	 * or as one file using the new Distribution packet) the system init contains validation data for those two files combined (e.g. the CRC value). This method may return
	 * <code>null</code> if there is no SoftDevice nor Bootloader in the ZIP or the DAT file is not present there.
	 * @return the content of the init packet for SoftDevice and/or Bootloader
	 */
	public byte[] getSystemInit() {
		return systemInitBytes;
	}

	/**
	 * Returns the content of the init file for the Application or <code>null</code> if no application file in the ZIP, or the DAT file is not provided.
	 * @return the content of the init packet for Application
	 */
	public byte[] getApplicationInit() {
		return applicationInitBytes;
	}
}
