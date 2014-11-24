package no.nordicsemi.android.dfu;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipHexInputStream extends ZipInputStream {
	private static final String SOFTDEVICE_NAME = "softdevice.(hex|bin)";
	private static final String BOOTLOADER_NAME = "bootloader.(hex|bin)";
	private static final String APPLICATION_NAME = "application.(hex|bin)";
	private static final String SYSTEM_INIT = "system.dat";
	private static final String APPLICATION_INIT = "application.dat";

	private byte[] softDeviceBytes;
	private byte[] bootloaderBytes;
	private byte[] applicationBytes;
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
	 * @param trim
	 *            if <code>true</code> the bin data will be trimmed. All data from addresses < 0x1000 will be skipped. In the Soft Device 7.0.0 it's MBR space and this HEX fragment should not be
	 *            transmitted. However, other DFU implementations (f.e. without Soft Device) may require uploading the whole file.
	 * @throws IOException
	 */
	public ZipHexInputStream(final InputStream stream, final int mbrSize, final int types) throws IOException {
		super(stream);

		this.bytesRead = 0;
		this.bytesReadFromCurrentSource = 0;

		try {
			ZipEntry ze;
			while ((ze = getNextEntry()) != null) {
				final String filename = ze.getName();
				final boolean softDevice = filename.matches(SOFTDEVICE_NAME);
				final boolean bootloader = filename.matches(BOOTLOADER_NAME);
				final boolean application = filename.matches(APPLICATION_NAME);
				final boolean systemInit = filename.matches(SYSTEM_INIT);
				final boolean applicationInit = filename.matches(APPLICATION_INIT);
				if (ze.isDirectory() || !(softDevice || bootloader || application || systemInit || applicationInit))
					throw new IOException("ZIP content not supported. Only " + SOFTDEVICE_NAME + ", " + BOOTLOADER_NAME + ", " + APPLICATION_NAME + ", " + SYSTEM_INIT + " or " + APPLICATION_INIT
							+ " are allowed.");

				// Skip files that are not specified in 'types'
				if (types != DfuBaseService.TYPE_AUTO && (
						((softDevice || systemInit) && (types & DfuBaseService.TYPE_SOFT_DEVICE) == 0) ||
								((bootloader || systemInit) && (types & DfuBaseService.TYPE_BOOTLOADER) == 0) ||
						((application || applicationInit) && (types & DfuBaseService.TYPE_APPLICATION) == 0))) {
					continue;
				}

				// Read file content to byte array
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				final byte[] buffer = new byte[1024];
				int count;
				while ((count = super.read(buffer)) != -1) {
					baos.write(buffer, 0, count);
				}
				byte[] source = baos.toByteArray();

				// Create HexInputStream from bytes and copy BIN content to arrays
				if (softDevice) {
					if (filename.endsWith("hex")) {
						final HexInputStream is = new HexInputStream(source, mbrSize);
						source = softDeviceBytes = new byte[softDeviceSize = is.available()];
						is.read(softDeviceBytes);
						is.close();
					} else {
						softDeviceBytes = source;
						softDeviceSize = source.length;
					}
					// upload must always start from Soft Device
					currentSource = source;
				} else if (bootloader) {
					if (filename.endsWith("hex")) {
						final HexInputStream is = new HexInputStream(source, mbrSize);
						source = bootloaderBytes = new byte[bootloaderSize = is.available()];
						is.read(bootloaderBytes);
						is.close();
					} else {
						bootloaderBytes = source;
						bootloaderSize = source.length;
					}
					// If the current source is null or application, switch it to the bootloader
					if (currentSource == applicationBytes)
						currentSource = source;
				} else if (application) {
					if (filename.endsWith("hex")) {
						final HexInputStream is = new HexInputStream(source, mbrSize);
						source = applicationBytes = new byte[applicationSize = is.available()];
						is.read(applicationBytes);
						is.close();
					} else {
						applicationBytes = source;
						applicationSize = source.length;
					}
					// Temporarily set the current source to application, it may be overwritten in a moment
					if (currentSource == null)
						currentSource = source;
				} else if (systemInit) {
					systemInitBytes = source;
				} else if (applicationInit) {
					applicationInitBytes = source;
				}
			}
		} finally {
			super.close();
		}
	}

	@Override
	public void close() throws IOException {
		softDeviceBytes = null;
		bootloaderBytes = null;
		softDeviceBytes = null;
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
			softDeviceSize = 0;
		}
		if ((t & DfuBaseService.TYPE_BOOTLOADER) == 0) {
			bootloaderBytes = null;
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
		byte[] ret = null;
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
	public int available() {
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

	public byte[] getSystemInit() {
		return systemInitBytes;
	}

	public byte[] getApplicationInit() {
		return applicationInitBytes;
	}
}
