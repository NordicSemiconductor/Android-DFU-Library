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

	public static String parse(final int error) {
		switch (error & (~DfuBaseService.ERROR_REMOTE_MASK)) {
			case OP_CODE_NOT_SUPPORTED:
				return "REMOTE DFU OP CODE NOT SUPPORTED";
			case INVALID_PARAM:
				return "REMOTE DFU INVALID PARAM";
			case INSUFFICIENT_RESOURCES:
				return "REMOTE DFU INSUFFICIENT RESOURCES";
			case INVALID_OBJECT:
				return "REMOTE DFU INVALID OBJECT";
			case UNSUPPORTED_TYPE:
				return "REMOTE DFU UNSUPPORTED TYPE";
			case OPERATION_NOT_PERMITTED:
				return "REMOTE DFU OPERATION NOT PERMITTED";
			case OPERATION_FAILED:
				return "REMOTE DFU OPERATION FAILED";
			case EXTENDED_ERROR:
				// The error details can be read using Read Error operation
				return "REMOTE DFU EXTENDED ERROR";
			default:
				return "UNKNOWN (" + error + ")";
		}
	}
}
