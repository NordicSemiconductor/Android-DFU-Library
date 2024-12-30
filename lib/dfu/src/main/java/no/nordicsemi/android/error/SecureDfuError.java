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

package no.nordicsemi.android.error;

import no.nordicsemi.android.dfu.DfuBaseService;

/**
 * This class contains the error codes that may occur during the Secure DFU process.
 */
public final class SecureDfuError {
	// DFU status values
	// public static final int SUCCESS = 1; // that's not an error
	/** The request is not supported by the DFU target. */
	public static final int OP_CODE_NOT_SUPPORTED = 2;
	/** Invalid parameter value. */
	public static final int INVALID_PARAM = 3;
	/** The device has no resources to perform the operation. */
	public static final int INSUFFICIENT_RESOURCES = 4;
	/** The object number if invalid. */
	public static final int INVALID_OBJECT = 5;
	/** The type of the object is invalid. */
	public static final int UNSUPPORTED_TYPE = 7;
	/** The requested operation is not permitted. */
	public static final int OPERATION_NOT_PERMITTED = 8;
	/** The requested operation failed. */
	public static final int OPERATION_FAILED = 10; // 0xA
	/** The error reason was returned as an extended error. */
	public static final int EXTENDED_ERROR = 11; // 0xB

	// public static final int EXT_ERROR_NO_ERROR = 0x00; // that's not an error
	/** Wrong command format. */
	public static final int EXT_ERROR_WRONG_COMMAND_FORMAT = 0x02;
	/** Unknown command. */
	public static final int EXT_ERROR_UNKNOWN_COMMAND = 0x03;
	/** Init command is not valid. */
	public static final int EXT_ERROR_INIT_COMMAND_INVALID = 0x04;
	/** FW version check failed. */
	public static final int EXT_ERROR_FW_VERSION_FAILURE = 0x05;
	/** HW version check failed. */
	public static final int EXT_ERROR_HW_VERSION_FAILURE = 0x06;
	/** SoftDevice version check failed. */
	public static final int EXT_ERROR_SD_VERSION_FAILURE = 0x07;
	/** Signature is missing. */
	public static final int EXT_ERROR_SIGNATURE_MISSING = 0x08;
	/** Wrong hash type. */
	public static final int EXT_ERROR_WRONG_HASH_TYPE = 0x09;
	/** Hash failed. */
	public static final int EXT_ERROR_HASH_FAILED = 0x0A;
	/** Wrong signature type. */
	public static final int EXT_ERROR_WRONG_SIGNATURE_TYPE = 0x0B;
	/** Verification failed. */
	public static final int EXT_ERROR_VERIFICATION_FAILED = 0x0C;
	/** Insufficient space for the image. */
	public static final int EXT_ERROR_INSUFFICIENT_SPACE = 0x0D;

	// public static final int BUTTONLESS_SUCCESS = 1;
	/** The request is not supported by the DFU target. */
	public static final int BUTTONLESS_ERROR_OP_CODE_NOT_SUPPORTED = 2;
	/** The requested operation failed. */
	public static final int BUTTONLESS_ERROR_OPERATION_FAILED = 4;

	private SecureDfuError() {
		// empty
	}

	/**
	 * Parses the error code and returns the error message.
	 *
	 * @param error the received error code
	 * @return the error message
	 */
	public static String parse(final int error) {
		switch (error) {
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | OP_CODE_NOT_SUPPORTED:		return "OP CODE NOT SUPPORTED";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | INVALID_PARAM:				return "INVALID PARAM";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | INSUFFICIENT_RESOURCES:		return "INSUFFICIENT RESOURCES";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | INVALID_OBJECT:				return "INVALID OBJECT";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | UNSUPPORTED_TYPE:			return "UNSUPPORTED TYPE";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | OPERATION_NOT_PERMITTED:		return "OPERATION NOT PERMITTED";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | OPERATION_FAILED:			return "OPERATION FAILED";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE | EXTENDED_ERROR:				return "EXTENDED ERROR";
			default:
				return "UNKNOWN (" + error + ")";
		}
	}

	/**
	 * Parses the extended error code and returns the error message.
	 *
	 * @param error the received extended error code
	 * @return the error message
	 */
	public static String parseExtendedError(final int error) {
		switch (error) {
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_WRONG_COMMAND_FORMAT: return "Wrong command format";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_UNKNOWN_COMMAND:		return "Unknown command";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_INIT_COMMAND_INVALID: return "Init command invalid";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_FW_VERSION_FAILURE:	return "FW version failure";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_HW_VERSION_FAILURE:	return "HW version failure";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_SD_VERSION_FAILURE:	return "SD version failure";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_SIGNATURE_MISSING :	return "Signature mismatch";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_WRONG_HASH_TYPE:		return "Wrong hash type";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_HASH_FAILED:			return "Hash failed";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_WRONG_SIGNATURE_TYPE: return "Wrong signature type";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_VERIFICATION_FAILED:	return "Verification failed";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_EXTENDED | EXT_ERROR_INSUFFICIENT_SPACE:	return "Insufficient space";
			default:
				return "Reserved for future use";
		}
	}

	/**
	 * Parses the error code returned by the DFU Buttonless service and returns the error message.
	 *
	 * @param error the received error code
	 * @return the error message
	 */
	public static String parseButtonlessError(final int error) {
		switch (error) {
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS | BUTTONLESS_ERROR_OP_CODE_NOT_SUPPORTED:	return "OP CODE NOT SUPPORTED";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS | BUTTONLESS_ERROR_OPERATION_FAILED:		return "OPERATION FAILED";
			default:
				return "UNKNOWN (" + error + ")";
		}
	}
}
