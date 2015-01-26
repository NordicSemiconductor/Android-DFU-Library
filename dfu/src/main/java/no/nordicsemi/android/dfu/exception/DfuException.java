/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. 
 * This heading must NOT be removed from the file.
 ******************************************************************************/
package no.nordicsemi.android.dfu.exception;

import no.nordicsemi.android.dfu.DfuBaseService;

public class DfuException extends Exception {
	private static final long serialVersionUID = -6901728550661937942L;

	private final int mError;

	public DfuException(final String message, final int state) {
		super(message);

		mError = state;
	}

	public int getErrorNumber() {
		return mError;
	}

	@Override
	public String getMessage() {
		return super.getMessage() + " (error " + (mError & ~DfuBaseService.ERROR_CONNECTION_MASK) + ")";
	}
}
