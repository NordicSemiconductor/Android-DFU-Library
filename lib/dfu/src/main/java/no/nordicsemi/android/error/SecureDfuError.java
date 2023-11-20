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

public final class SecureDfuError {
	// DFU status values
	// public static final int SUCCESS = 1; // that's not an error
	public static final int OP_CODE_NOT_SUPPORTED = 2;
	public static final int INVALID_PARAM = 3;
	public static final int INSUFFICIENT_RESOURCES = 4;
	public static final int INVALID_OBJECT = 5;
	public static final int UNSUPPORTED_TYPE = 7;
	public static final int OPERATION_NOT_PERMITTED = 8;
	public static final int OPERATION_FAILED = 10; // 0xA
	public static final int EXTENDED_ERROR = 11; // 0xB

	// public static final int EXT_ERROR_NO_ERROR = 0x00; // that's not an error
	public static final int EXT_ERROR_WRONG_COMMAND_FORMAT = 0x02;
	public static final int EXT_ERROR_UNKNOWN_COMMAND = 0x03;
	public static final int EXT_ERROR_INIT_COMMAND_INVALID = 0x04;
	public static final int EXT_ERROR_FW_VERSION_FAILURE = 0x05;
	public static final int EXT_ERROR_HW_VERSION_FAILURE = 0x06;
	public static final int EXT_ERROR_SD_VERSION_FAILURE = 0x07;
	public static final int EXT_ERROR_SIGNATURE_MISSING = 0x08;
	public static final int EXT_ERROR_WRONG_HASH_TYPE = 0x09;
	public static final int EXT_ERROR_HASH_FAILED = 0x0A;
	public static final int EXT_ERROR_WRONG_SIGNATURE_TYPE = 0x0B;
	public static final int EXT_ERROR_VERIFICATION_FAILED = 0x0C;
	public static final int EXT_ERROR_INSUFFICIENT_SPACE = 0x0D;

	// public static final int BUTTONLESS_SUCCESS = 1;
	public static final int BUTTONLESS_ERROR_OP_CODE_NOT_SUPPORTED = 2;
	public static final int BUTTONLESS_ERROR_OPERATION_FAILED = 4;

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

	public static String parseButtonlessError(final int error) {
		switch (error) {
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS | BUTTONLESS_ERROR_OP_CODE_NOT_SUPPORTED:	return "OP CODE NOT SUPPORTED";
			case DfuBaseService.ERROR_REMOTE_TYPE_SECURE_BUTTONLESS | BUTTONLESS_ERROR_OPERATION_FAILED:		return "OPERATION FAILED";
			default:
				return "UNKNOWN (" + error + ")";
		}
	}
}
