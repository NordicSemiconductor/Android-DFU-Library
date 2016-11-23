/*************************************************************************************************************************************************
 * Copyright (c) 2016, Nordic Semiconductor
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

	public static String parse(final int error) {
		switch (error & (~DfuBaseService.ERROR_REMOTE_MASK)) {
			case OP_CODE_NOT_SUPPORTED:			return "REMOTE DFU OP CODE NOT SUPPORTED";
			case INVALID_PARAM:					return "REMOTE DFU INVALID PARAM";
			case INSUFFICIENT_RESOURCES:		return "REMOTE DFU INSUFFICIENT RESOURCES";
			case INVALID_OBJECT:				return "REMOTE DFU INVALID OBJECT";
			case UNSUPPORTED_TYPE:				return "REMOTE DFU UNSUPPORTED TYPE";
			case OPERATION_NOT_PERMITTED:		return "REMOTE DFU OPERATION NOT PERMITTED";
			case OPERATION_FAILED:				return "REMOTE DFU OPERATION FAILED";
			case EXTENDED_ERROR:				return "REMOTE DFU EXTENDED ERROR";
			default:							return "UNKNOWN (" + error + ")";
		}
	}

	public static String parseExtendedError(final int error) {
		switch (error) {
			case EXT_ERROR_WRONG_COMMAND_FORMAT: return "Wrong command format";
			case EXT_ERROR_UNKNOWN_COMMAND:		 return "Unknown command";
			case EXT_ERROR_INIT_COMMAND_INVALID: return "Init command invalid";
			case EXT_ERROR_FW_VERSION_FAILURE:	 return "FW version failure";
			case EXT_ERROR_HW_VERSION_FAILURE:	 return "HW version failure";
			case EXT_ERROR_SD_VERSION_FAILURE:	 return "SD version failure";
			case EXT_ERROR_SIGNATURE_MISSING :	 return "Signature mismatch";
			case EXT_ERROR_WRONG_HASH_TYPE:		 return "Wrong hash type";
			case EXT_ERROR_HASH_FAILED:			 return "Hash failed";
			case EXT_ERROR_WRONG_SIGNATURE_TYPE: return "Wring signature type";
			case EXT_ERROR_VERIFICATION_FAILED:	 return "Verification failed";
			case EXT_ERROR_INSUFFICIENT_SPACE:	 return "Insufficient space";
			default:							 return "Reserved for future use";
		}
	}
}
