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

package no.nordicsemi.android.dfu.internal;

import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import androidx.annotation.NonNull;
import no.nordicsemi.android.dfu.DfuBaseService;
import no.nordicsemi.android.dfu.internal.manifest.FileInfo;
import no.nordicsemi.android.dfu.internal.manifest.Manifest;
import no.nordicsemi.android.dfu.internal.manifest.ManifestFile;
import no.nordicsemi.android.dfu.internal.manifest.SoftDeviceBootloaderFileInfo;

/**
 * <p>
 * Reads the firmware files from the a ZIP file. The ZIP file must be either created using the
 * <a href="https://github.com/NordicSemiconductor/pc-nrfutil"><b>nrf util</b></a>,
 * or follow the backward compatibility syntax: must contain only files with names:
 * application.hex/bin, softdevice.hex/dat or bootloader.hex/bin, optionally also application.dat
 * and/or system.dat with init packets.
 * <p>
 * The ArchiveInputStream will read only files with types specified by <b>types</b> parameter of
 * the constructor.
 */
public class ArchiveInputStream extends InputStream {
	private static final String TAG = "DfuArchiveInputStream";

	/**
	 * The name of the manifest file is fixed.
	 */
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

	private final ZipInputStream zipInputStream;

	/**
	 * Contains bytes arrays with BIN files. HEX files are converted to BIN before being
     * added to this map.
	 */
	private final Map<String, byte[]> entries;
	private final CRC32 crc32;
	private Manifest manifest;

	private byte[] applicationBytes;
	private byte[] softDeviceBytes;
	private byte[] bootloaderBytes;
	private byte[] softDeviceAndBootloaderBytes;
	private byte[] systemInitBytes;
	private byte[] applicationInitBytes;
	private byte[] currentSource;
	private int type;
	private int bytesReadFromCurrentSource;
	private int softDeviceSize;
	private int bootloaderSize;
	private int applicationSize;
	private int bytesRead;

	private byte[] markedSource;
	private int bytesReadFromMarkedSource;

	/**
	 * <p>
	 * The ArchiveInputStream read HEX or BIN files from the Zip stream. It may skip some of them,
     * depending on the value of the types parameter. This is useful if the DFU service wants to
     * send the Soft Device and Bootloader only, and then the Application in the following connection,
     * despite the ZIP file contains all 3 HEX/BIN files.
	 * When types is equal to {@link DfuBaseService#TYPE_AUTO} all present files are read.
	 * <p>
     * Use bit combination of the following types:
	 * <ul>
	 * <li>{@link DfuBaseService#TYPE_SOFT_DEVICE}</li>
	 * <li>{@link DfuBaseService#TYPE_BOOTLOADER}</li>
	 * <li>{@link DfuBaseService#TYPE_APPLICATION}</li>
	 * <li>{@link DfuBaseService#TYPE_AUTO}</li>
	 * </ul>
	 *
	 * @param stream  the Zip Input Stream
	 * @param mbrSize The size of the MRB segment (Master Boot Record) on the device.
     *                The parser will cut data from addresses below that number from all HEX files.
	 * @param types   File types that are to be read from the ZIP. Use
     *                {@link DfuBaseService#TYPE_APPLICATION} etc.
	 * @throws java.io.IOException Thrown in case of an invalid ZIP file.
	 */
	public ArchiveInputStream(final InputStream stream, final int mbrSize, final int types)
            throws IOException {
		// Check if the file is not too big. It's hard to say what would be considered too big, but
		// 10 MB should be enough for any firmware package. If not, fork the library and change this.
		if (stream.available() > 10 * 1024 * 1024) {
			throw new IOException("File too large: " + stream.available() + " bytes (max 10 MB)");
		}
		this.zipInputStream = new ZipInputStream(stream);

		this.crc32 = new CRC32();
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
			boolean valid = false;
			if (manifest != null) {
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
			mark(0);
		} finally {
			type = getContentType();
			zipInputStream.close();
		}
	}

	/**
	 * Validates the path (not the content) of the zip file to prevent path traversal issues.
	 *
	 * <p> When unzipping an archive, always validate the compressed files' paths and reject any path
	 * that has a path traversal (such as ../..). Simply looking for .. characters in the compressed
	 * file's path may not be enough to prevent path traversal issues. The code validates the name of
	 * the entry before extracting the entry. If the name is invalid, the entire extraction is aborted.
	 * <p>
	 *
	 * @param filename The path to the file.
	 * @param intendedDir The intended directory where the zip should be.
	 * @return The validated path to the file.
	 * @throws java.io.IOException Thrown in case of path traversal issues.
	 */
	@SuppressWarnings("SameParameterValue")
	private String validateFilename(@NonNull final String filename,
									@NonNull final String intendedDir)
			throws java.io.IOException {
		File f = new File(filename);
		String canonicalPath = f.getCanonicalPath();

		File iD = new File(intendedDir);
		String canonicalID = iD.getCanonicalPath();

		if (canonicalPath.startsWith(canonicalID)) {
			return canonicalPath.substring(1); // remove leading "/"
		} else {
			throw new IllegalStateException("File is outside extraction target directory.");
		}
	}

	/**
	 * Reads all files into byte arrays.
	 * Here we don't know whether the ZIP file is valid.
	 * <p>
	 * The ZIP file is valid when contains a 'manifest.json' file and all BIN and DAT files that
     * are specified in the manifest.
	 * <p>
	 * For backwards compatibility ArchiveInputStream supports also ZIP archives without
     * 'manifest.json' file but than it MUST include at least one of the following files:
     * softdevice.bin/hex, bootloader.bin/hex, application.bin/hex.
	 * To support the init packet such ZIP file should contain also application.dat and/or system.dat
     * (with the CRC16 of a SD, BL or SD+BL together).
	 */
	private void parseZip(final int mbrSize) throws IOException {
		final byte[] buffer = new byte[1024];
		String manifestData = null;

		ZipEntry ze;
		while ((ze = zipInputStream.getNextEntry()) != null) {
			final String filename = validateFilename(ze.getName(), ".");

			if (ze.isDirectory()) {
				Log.w(TAG, "A directory found in the ZIP: " + filename + "!");
				continue;
			}

			// Read file content to byte array
			final ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int count;
			while ((count = zipInputStream.read(buffer)) != -1) {
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
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
					manifestData = new String(source, StandardCharsets.UTF_8);
				} else {
					//noinspection CharsetObjectCanBeUsed
					manifestData = new String(source, "UTF-8");
				}
			else
				entries.put(filename, source);
		}

		// Some validation
		if (entries.isEmpty()) {
			throw new FileNotFoundException("No files found in the ZIP. Check if the URI provided is " +
                    "valid and the ZIP contains required files on root level, not in a directory.");
		}

		if (manifestData != null) {
			final ManifestFile manifestFile = new Gson().fromJson(manifestData, ManifestFile.class);
			manifest = manifestFile.getManifest();
			if (manifest == null) {
				Log.w(TAG, "Manifest failed to be parsed. Did you add \n" +
						"-keep class no.nordicsemi.android.dfu.** { *; }\n" +
						"to your proguard rules?");
			}
		} else {
			Log.w(TAG, "Manifest not found in the ZIP. It is recommended to use a distribution " +
                    "file created with: https://github.com/NordicSemiconductor/pc-nrfutil/ (for Legacy DFU use version 0.5.x)");
		}
	}

	@Override
	public void close() throws IOException {
		softDeviceBytes = null;
		bootloaderBytes = null;
		applicationBytes = null;
		softDeviceAndBootloaderBytes = null;
		softDeviceSize = bootloaderSize = applicationSize = 0;
		currentSource = null;
		bytesRead = bytesReadFromCurrentSource = 0;
		zipInputStream.close();
	}

	@Override
	public long skip(final long n) {
		return 0;
	}

	@Override
	public int read() {
		final byte[] buffer = new byte[1];
		if (read(buffer) == -1) {
			return -1;
		} else {
			return buffer[0] & 0xFF;
		}
	}

	@Override
	public int read(@NonNull final byte[] buffer) {
		return read(buffer, 0, buffer.length);
	}

	@Override
	public int read(@NonNull final byte[] buffer, final int offset, final int length) {
		final int size = rawRead(buffer, offset, length);
		if (length > size && startNextFile() != null) {
			return size + rawRead(buffer, offset + size, length - size);
		}
		return size;
	}

	private int rawRead(@NonNull final byte[] buffer, final int offset, final int length) {
		final int maxSize = currentSource.length - bytesReadFromCurrentSource;
		final int size = Math.min(length, maxSize);
		System.arraycopy(currentSource, bytesReadFromCurrentSource, buffer, offset, size);
		bytesReadFromCurrentSource += size;
		bytesRead += size;
		crc32.update(buffer, offset, size);
		return size;
	}

	@Override
	public boolean markSupported() {
		return true;
	}

	/**
	 * Marks the current position in the stream. The parameter is ignored.
	 *
	 * @param readlimit this parameter is ignored, can be anything
	 */
	@Override
	public void mark(final int readlimit) {
		markedSource = currentSource;
		bytesReadFromMarkedSource = bytesReadFromCurrentSource;
	}

	@Override
	public void reset() {
		currentSource = markedSource;
		bytesRead = bytesReadFromCurrentSource = bytesReadFromMarkedSource;

		// Restore the CRC to the value is was on mark.
		crc32.reset();
		if (currentSource == bootloaderBytes && softDeviceBytes != null) {
			crc32.update(softDeviceBytes);
			bytesRead += softDeviceSize;
		}
		crc32.update(currentSource, 0, bytesReadFromCurrentSource);
	}

	/**
	 * Resets to the beginning of current stream.
	 * If SD and BL were updated, the stream will be reset to the beginning.
	 * If SD and BL were already sent and the current stream was changed to application,
	 * this method will reset to the beginning of the application stream.
	 */
	public void fullReset() {
		// Reset stream to SoftDevice if SD and BL firmware were given separately
		if (softDeviceBytes != null && bootloaderBytes != null && currentSource == bootloaderBytes) {
			currentSource = softDeviceBytes;
		}
		// Reset the bytes count to 0
		bytesReadFromCurrentSource = 0;
		mark(0);
		reset();
	}

	/**
	 * Returns number of bytes read until now.
	 */
	public int getBytesRead() {
		return bytesRead;
	}

	/**
	 * Returns the CRC32 of the part of the firmware that was already read.
	 *
	 * @return the CRC
	 */
	public long getCrc32() {
		return crc32.getValue();
	}

	/**
	 * Returns the content type based on the content of the ZIP file. The content type may be
     * truncated using {@link #setContentType(int)}.
	 *
	 * @return A bit field of {@link DfuBaseService#TYPE_SOFT_DEVICE TYPE_SOFT_DEVICE},
     * {@link DfuBaseService#TYPE_BOOTLOADER TYPE_BOOTLOADER} and
     * {@link DfuBaseService#TYPE_APPLICATION TYPE_APPLICATION}
	 */
	public int getContentType() {
		type = 0;
		// In Secure DFU the softDeviceSize and bootloaderSize may be 0 if both are in the ZIP file.
        // The size of each part is embedded in the Init packet.
		if (softDeviceAndBootloaderBytes != null)
			type |= DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER;
		// In Legacy DFU the size of each of these parts was given in the manifest file.
		if (softDeviceSize > 0)
			type |= DfuBaseService.TYPE_SOFT_DEVICE;
		if (bootloaderSize > 0)
			type |= DfuBaseService.TYPE_BOOTLOADER;
		if (applicationSize > 0)
			type |= DfuBaseService.TYPE_APPLICATION;
		return type;
	}

	/**
	 * Truncates the current content type. May be used to hide some files, e.g. to send Soft Device
     * and Bootloader without Application or only the Application.
	 *
	 * @param type the new type.
	 * @return The final type after truncating.
	 */
	public int setContentType(final int type) {
		this.type = type;
		// If the new type has Application, but there is no application fw, remove this type bit
		if ((type & DfuBaseService.TYPE_APPLICATION) > 0 && applicationBytes == null)
			this.type &= ~DfuBaseService.TYPE_APPLICATION;
		// If the new type has SD+BL
		if ((type & (DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER)) == (DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER)) {
			// but there is no SD, remove the softdevice type bit
			if (softDeviceBytes == null && softDeviceAndBootloaderBytes == null)
				this.type &= ~DfuBaseService.TYPE_SOFT_DEVICE;
			// or there is no BL, remove the bootloader type bit
			if (bootloaderBytes == null && softDeviceAndBootloaderBytes == null)
				this.type &= ~DfuBaseService.TYPE_SOFT_DEVICE;
		} else {
			// If at least one of SD or B: bit is cleared, but the SD+BL file is set, remove both bits.
			if (softDeviceAndBootloaderBytes != null)
				this.type &= ~(DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER);
		}

		if ((type & (DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER)) > 0 && softDeviceAndBootloaderBytes != null)
			currentSource = softDeviceAndBootloaderBytes;
		else if ((type & DfuBaseService.TYPE_SOFT_DEVICE) > 0)
			currentSource = softDeviceBytes;
		else if ((type & DfuBaseService.TYPE_BOOTLOADER) > 0)
			currentSource = bootloaderBytes;
		else if ((type & DfuBaseService.TYPE_APPLICATION) > 0)
			currentSource = applicationBytes;
		bytesReadFromCurrentSource = 0;
		mark(0);
		reset();
		return this.type;
	}

	/**
	 * Sets the currentSource to the new file or to <code>null</code> if the last file has been
     * transmitted.
	 *
	 * @return The new source, the same as {@link #currentSource}.
	 */
	private byte[] startNextFile() {
		byte[] ret;
		if (currentSource == softDeviceBytes && bootloaderBytes != null && (type & DfuBaseService.TYPE_BOOTLOADER) > 0) {
			ret = currentSource = bootloaderBytes;
		} else if (currentSource != applicationBytes && applicationBytes != null && (type & DfuBaseService.TYPE_APPLICATION) > 0) {
			ret = currentSource = applicationBytes;
		} else {
			ret = currentSource = null;
		}
		bytesReadFromCurrentSource = 0;
		return ret;
	}

	/**
	 * Returns the number of bytes that has not been read yet. This value includes only
     * firmwares matching the content type set by the constructor or the
     * {@link #setContentType(int)} method.
	 */
	@Override
	public int available() {
		// In Secure DFU softdevice and bootloader sizes are not provided in the Init file
        // (they are encoded inside the Init file instead). The service doesn't send those sizes,
        // not the whole size of the firmware separately, like it was done in the Legacy DFU.
		// This method then is just used to log file size.

		// In case of SD+BL in Secure DFU:
		if (softDeviceAndBootloaderBytes != null && softDeviceSize == 0 && bootloaderSize == 0
				&& (type & (DfuBaseService.TYPE_SOFT_DEVICE | DfuBaseService.TYPE_BOOTLOADER)) > 0)
			return softDeviceAndBootloaderBytes.length + applicationImageSize() - bytesRead;

		// Otherwise:
		return softDeviceImageSize() + bootloaderImageSize() + applicationImageSize() - bytesRead;
	}

	/**
	 * Returns the total size of the SoftDevice firmware. In case the firmware was given as a HEX,
     * this method returns the size of the BIN content of the file.
	 *
	 * @return The size of the SoftDevice firmware (BIN part).
	 */
	public int softDeviceImageSize() {
		return (type & DfuBaseService.TYPE_SOFT_DEVICE) > 0 ? softDeviceSize : 0;
	}

	/**
	 * Returns the total size of the Bootloader firmware. In case the firmware was given as a HEX,
     * this method returns the size of the BIN content of the file.
	 *
	 * @return The size of the Bootloader firmware (BIN part).
	 */
	public int bootloaderImageSize() {
		return (type & DfuBaseService.TYPE_BOOTLOADER) > 0 ? bootloaderSize : 0;
	}

	/**
	 * Returns the total size of the Application firmware. In case the firmware was given as a HEX,
     * this method returns the size of the BIN content of the file.
	 *
	 * @return The size of the Application firmware (BIN part).
	 */
	public int applicationImageSize() {
		return (type & DfuBaseService.TYPE_APPLICATION) > 0 ? applicationSize : 0;
	}

	/**
	 * Returns the content of the init file for SoftDevice and/or Bootloader. When both SoftDevice
     * and Bootloader are present in the ZIP file (as two files using the compatibility mode
	 * or as one file using the new Distribution packet) the system init contains validation data
     * for those two files combined (e.g. the CRC value). This method may return
	 * <code>null</code> if there is no SoftDevice nor Bootloader in the ZIP or the DAT file is
     * not present there.
	 *
	 * @return The content of the init packet for SoftDevice and/or Bootloader.
	 */
	public byte[] getSystemInit() {
		return systemInitBytes;
	}

	/**
	 * Returns the content of the init file for the Application or <code>null</code> if no
     * application file in the ZIP, or the DAT file is not provided.
	 *
	 * @return The content of the init packet for Application.
	 */
	public byte[] getApplicationInit() {
		return applicationInitBytes;
	}

	/**
	 * This method returns true if the content of the ZIP file may be sent only using Secure DFU.
	 * The reason may be that the ZIP contains a single bin file with SD and/or BL together with
     * App, which has to be sent in a single connection.
	 * Sizes of each component are not given explicitly in the Manifest (even if they are,
     * they are ignored). They are hidden in the Init Packet instead.
	 *
	 * @return True if the content of this ZIP may only be sent using Secure DFU.
	 */
	public boolean isSecureDfuRequired() {
		return manifest != null && manifest.isSecureDfuRequired();
	}
}
