package no.nordicsemi.android.dfu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import android.util.Log;

public class ZipHexInputStream extends ZipInputStream {
	private static final String SOFTDEVICE_NAME = "softdevice.hex";
	private static final String BOOTLOADER_NAME = "bootloader.hex";
	private static final String APPLICATION_NAME = "application.hex";

	private byte[] softDeviceBytes;
	private byte[] bootloaderBytes;
	private byte[] applicationBytes;
	private byte[] currentSource;
	private int bytesReadFromCurrentSource;
	private int softDeviceSize;
	private int bootloaderSize;
	private int applicationSize;
	private int bytesRead;

	/**
	 * <p>
	 * The {@link ZipHexInputStream} read HEX files from the Zip stream. It may skip some of them, depending on the value of types parameter. This is useful if the service wants to send the Soft
	 * Device and Bootloader only, and then Application in the next connection despite that ZIP file contains all 3 HEX files. When types is equal to {@link DfuBaseService#TYPE_AUTO} all present files
	 * are read.
	 * </p>
	 * <p>
	 * Use bit combination of the following types:
	 * <ul>
	 * <li>{@link DfuBaseService#TYPE_SOFT_DEVICE}</li>
	 * <li>{@link DfuBaseService#TYPE_BOOTLOADER}</li>
	 * <li>{@link DfuBaseService#TYPE_APPLICATION}</li>
	 * <li>{@link DfuBaseService#TYPE_AUTO}</li>
	 * </ul>
	 * </p>
	 * 
	 * @param stream
	 *            the Zip Input Stream
	 * @param types
	 *            files to read
	 * @throws IOException
	 */
	public ZipHexInputStream(final InputStream stream, final byte types) throws IOException {
		super(stream);

		this.bytesRead = 0;
		this.bytesReadFromCurrentSource = 0;

		try {
			ZipEntry ze;
			while ((ze = getNextEntry()) != null) {
				final String filename = ze.getName();
				final boolean softDevice = SOFTDEVICE_NAME.matches(filename);
				final boolean bootloader = BOOTLOADER_NAME.matches(filename);
				final boolean application = APPLICATION_NAME.matches(filename);
				if (ze.isDirectory() || !(softDevice || bootloader || application))
					throw new IOException("ZIP content not supported. Only " + SOFTDEVICE_NAME + ", " + BOOTLOADER_NAME + " or " + APPLICATION_NAME + " are allowed.");

				// Skip files that not specified in types
				if (types != DfuBaseService.TYPE_AUTO
						&& ((softDevice && (types & DfuBaseService.TYPE_SOFT_DEVICE) == 0) || (bootloader && (types & DfuBaseService.TYPE_BOOTLOADER) == 0) || (application && (types & DfuBaseService.TYPE_APPLICATION) == 0))) {
					Log.i("ZHIS", "Skipping: " + filename);
					continue;
				}

				// Read file content to byte array
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final byte[] buffer = new byte[1024];
				int count;
				while ((count = super.read(buffer)) != -1) {
					baos.write(buffer, 0, count);
				}
				final byte[] bytes = baos.toByteArray();

				// Create HexInputStream from bytes and copy BIN content to arrays
				byte[] source = null;
				final HexInputStream is = new HexInputStream(bytes);
				if (softDevice) {
					source = softDeviceBytes = new byte[softDeviceSize = is.available()];
					is.read(softDeviceBytes);
					// upload must always start from Soft Device
					currentSource = source;
				} else if (bootloader) {
					source = bootloaderBytes = new byte[bootloaderSize = is.available()];
					is.read(bootloaderBytes);
					// If the current source is null or application, switch it to the bootloader
					if (currentSource == applicationBytes)
						currentSource = source;
				} else if (application) {
					source = applicationBytes = new byte[applicationSize = is.available()];
					is.read(applicationBytes);
					// Temporarly set the current source to application, it may be overwritten in a moment
					if (currentSource == null)
						currentSource = source;
				}
				is.close();
				Log.i("ZHIS", filename + " loaded (bin size: " + is.sizeInBytes() + ")");
			}
		} finally {
			close();
		}
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
				Log.i("ZHIS", "End of all files, " + bytesRead + " bytes read in total");
				return size;
			}

			maxSize = currentSource.length;
			final int nextSize = buffer.length - size <= maxSize ? buffer.length - size : maxSize;
			System.arraycopy(currentSource, 0, buffer, size, nextSize);
			bytesReadFromCurrentSource += nextSize;
			size += nextSize;
			Log.i("ZHIS", "New file started, size = " + size);
		}
		bytesRead += size;
		return size;
	}

	public byte getContentType() {
		byte b = 0;
		if (softDeviceSize > 0)
			b |= DfuBaseService.TYPE_SOFT_DEVICE;
		if (bootloaderSize > 0)
			b |= DfuBaseService.TYPE_BOOTLOADER;
		if (applicationSize > 0)
			b |= DfuBaseService.TYPE_APPLICATION;
		return b;
	}

	private byte[] startNextFile() {
		byte[] ret = null;
		if (currentSource == softDeviceBytes && bootloaderBytes != null) {
			Log.i("ZHIS", "Switching to bootloader");
			ret = currentSource = bootloaderBytes;
		} else if (currentSource != applicationBytes && applicationBytes != null) {
			Log.i("ZHIS", "Switching to application");
			ret = currentSource = applicationBytes;
		} else {
			ret = currentSource = null;
		}
		bytesReadFromCurrentSource = 0;
		return ret;
	}

	@Override
	public int available() throws IOException {
		return softDeviceSize + bootloaderSize + applicationSize - bytesRead;
	}

	public int softDeviceImageSize() {
		return softDeviceSize;
	}

	public int bootloaderImageSize() {
		return bootloaderSize;
	}

	public int applicationImageSize() {
		return applicationSize;
	}
}
