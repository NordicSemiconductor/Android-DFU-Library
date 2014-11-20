package no.nordicsemi.android.dfu.scanner;

/**
 * When using the buttonless jump from the application mode to the DFU bootloader the bootloader, depending on the version, may start advertising with the same device address or one
 * with the last byte incremented by 1. Before the jump the application does not know which DFU bootloader version is installed on the DFU target device. The experimental version
 * in the SDK 7.0 and 7.1 will advertise with the same address. Since the SDK 8.0 version, in order to be consistent with the BLE specification, it uses a different address as it is
 * changing the services.
 * <p>
 * The scanner is being used only with unpaired devices. When bonded, the device will always keep the same address and preserve the Long Term Key while in the bootloader mode. It will notify the
 * application about the DFU services using the Service Changed indication.
 * </p>
 */
public interface BootloaderScanner {
	/**
	 * After the buttonless jump from the application mode to the bootloader mode the service will wait this long for the advertising bootloader (in milliseconds).
	 */
	public final static long TIMEOUT = 2000l; // ms
	/** The bootloader may advertise with the same address or one with the last byte incremented by this value. F.e. 00:11:22:33:44:55 -> 00:11:22:33:44:56. FF changes to 00. */
	public final static int ADDRESS_DIFF = 1;

	/**
	 * Searches for the advertising bootloader. The bootloader may advertise with the same device address or one with the last byte incremented by 1.
	 * This method is a blocking one and ends when such device is found. There are two implementations of this interface - one for old Androids and one for
	 * the Android 5+ devices.
	 * 
	 * @param deviceAddress
	 *            the application device address
	 * @return the address of the advertising DFU bootloader. If may be the same as the application address or one with the last byte incremented by 1 (AA:BB:CC:DD:EE:45/FF -> AA:BB:CC:DD:EE:46/00).
	 */
	public String searchFor(final String deviceAddress);
}
