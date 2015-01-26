/*******************************************************************************
 * Copyright (c) 2013 Nordic Semiconductor. All Rights Reserved.
 * 
 * The information contained herein is property of Nordic Semiconductor ASA.
 * Terms and conditions of usage are described in detail in NORDIC SEMICONDUCTOR STANDARD SOFTWARE LICENSE AGREEMENT.
 * Licensees are granted free, non-transferable use of the information. NO WARRANTY of ANY KIND is provided. 
 * This heading must NOT be removed from the file.
 ******************************************************************************/
package no.nordicsemi.android.dfu.exception;

public class RemoteDfuException extends Exception {
	private static final long serialVersionUID = -6901728550661937942L;

	private final int mState;

	public RemoteDfuException(final String message, final int state) {
		super(message);

		mState = state;
	}

	public int getErrorNumber() {
		return mState;
	}

	@Override
	public String getMessage() {
		return super.getMessage() + " (error " + mState + ")";
	}
}
