package no.nordicsemi.android.dfu.scanner;

import android.os.Build;

public class BootloaderScannerFactory {

	/**
	 * Returns the scanner implementation.
	 * 
	 * @return the bootloader scanner
	 */
	public static BootloaderScanner getScanner() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
			return new BootloaderScannerLollipop();
		return new BootloaderScannerJB();
	}
}
