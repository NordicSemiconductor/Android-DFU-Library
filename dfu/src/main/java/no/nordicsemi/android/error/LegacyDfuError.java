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

public final class LegacyDfuError {
	// DFU status values
	// public static final int SUCCESS = 1; // that's not an error
	public static final int INVALID_STATE = 2;
	public static final int NOT_SUPPORTED = 3;
	public static final int DATA_SIZE_EXCEEDS_LIMIT = 4;
	public static final int CRC_ERROR = 5;
	public static final int OPERATION_FAILED = 6;

	public static String parse(final int error) {
		switch (error & (~DfuBaseService.ERROR_REMOTE_MASK)) {
			case INVALID_STATE:				return "REMOTE DFU INVALID STATE";
			case NOT_SUPPORTED:				return "REMOTE DFU NOT SUPPORTED";
			case DATA_SIZE_EXCEEDS_LIMIT:	return "REMOTE DFU DATA SIZE EXCEEDS LIMIT";
			case CRC_ERROR:					return "REMOTE DFU INVALID CRC ERROR";
			case OPERATION_FAILED:			return "REMOTE DFU OPERATION FAILED";
			default:						return "UNKNOWN (" + error + ")";
		}
	}
}
