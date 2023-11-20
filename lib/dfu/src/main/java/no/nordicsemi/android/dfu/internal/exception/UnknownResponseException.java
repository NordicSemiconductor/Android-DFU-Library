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

package no.nordicsemi.android.dfu.internal.exception;

import java.util.Locale;

public class UnknownResponseException extends Exception {
	private static final long serialVersionUID = -8716125467309979289L;
	private static final char[] HEX_ARRAY = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };

	private final byte[] mResponse;
	private final int mExpectedReturnCode;
	private final int mExpectedOpCode;

	public UnknownResponseException(final String message, final byte[] response, final int expectedReturnCode, final int expectedOpCode) {
		super(message);

		mResponse = response != null ? response : new byte[0];
		mExpectedReturnCode = expectedReturnCode;
		mExpectedOpCode = expectedOpCode;
	}

	@Override
	public String getMessage() {
		return String.format(Locale.US, "%s (response: %s, expected: 0x%02X%02X..)", super.getMessage(), bytesToHex(mResponse, 0, mResponse.length), mExpectedReturnCode, mExpectedOpCode);
	}

	public static String bytesToHex(final byte[] bytes, final int start, final int length) {
		if (bytes == null || bytes.length <= start || length <= 0)
			return "";

		final int maxLength = Math.min(length, bytes.length - start);
		final char[] hexChars = new char[maxLength * 2];
		for (int j = 0; j < maxLength; j++) {
			final int v = bytes[start + j] & 0xFF;
			hexChars[j * 2] = HEX_ARRAY[v >>> 4];
			hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
		}
		return "0x" + new String(hexChars);
	}
}
