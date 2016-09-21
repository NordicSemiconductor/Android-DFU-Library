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

package no.nordicsemi.android.dfu;

import android.os.SystemClock;
import android.support.annotation.NonNull;

/* package */ class DfuProgressInfo {
	interface ProgressListener {
		void updateProgressNotification();
	}

	private final ProgressListener mListener;
	private int progress;
	private int bytesSent;
	private int initialBytesSent;
	private int lastBytesSent;
	private int bytesReceived;
	private int imageSizeInBytes;
	private int maxObjectSizeInBytes;
	private int currentPart;
	private int totalParts;
	private long timeStart, lastProgressTime;

	DfuProgressInfo(final @NonNull ProgressListener listener) {
		mListener = listener;
	}

	DfuProgressInfo init(final int imageSizeInBytes, final int currentPart, final int totalParts) {
		this.imageSizeInBytes = imageSizeInBytes;
		this.maxObjectSizeInBytes = Integer.MAX_VALUE; // by default the whole firmware will be sent as a single object
		this.currentPart = currentPart;
		this.totalParts = totalParts;
		return this;
	}

	DfuProgressInfo setTotalPart(final int totalParts) {
		this.totalParts = totalParts;
		return this;
	}

	public void setProgress(final int progress) {
		this.progress = progress;
		mListener.updateProgressNotification();
	}

	void setBytesSent(final int bytesSent) {
		if (timeStart == 0) {
			timeStart = SystemClock.elapsedRealtime();
			initialBytesSent = bytesSent;
		}
		this.bytesSent = bytesSent;
		this.progress = (int) (100.0f * bytesSent / imageSizeInBytes);
		mListener.updateProgressNotification();
	}

	void addBytesSent(final int increment) {
		setBytesSent(bytesSent + increment);
	}

	void setBytesReceived(final int bytesReceived) {
		this.bytesReceived = bytesReceived;
	}

	void setMaxObjectSizeInBytes(final int bytes) {
		this.maxObjectSizeInBytes = bytes;
	}

	boolean isComplete() {
		return bytesSent == imageSizeInBytes;
	}

	boolean isObjectComplete() {
		return (bytesSent % maxObjectSizeInBytes) == 0;
	}

	int getAvailableObjectSizeIsBytes() {
		final int remainingBytes = imageSizeInBytes - bytesSent;
		final int remainingChunk = maxObjectSizeInBytes - (bytesSent % maxObjectSizeInBytes);
		return Math.min(remainingBytes, remainingChunk);
	}

	int getProgress() {
		return progress;
	}

	int getBytesSent() {
		return bytesSent;
	}

	int getBytesReceived() {
		return bytesReceived;
	}

	int getImageSizeInBytes() {
		return imageSizeInBytes;
	}

	float getSpeed() {
		final long now = SystemClock.elapsedRealtime();
		final float speed = now - timeStart != 0 ? (float) (bytesSent - lastBytesSent) / (float) (now - lastProgressTime) : 0.0f;
		lastProgressTime = now;
		lastBytesSent = bytesSent;
		return speed;
	}

	float getAverageSpeed() {
		final long now = SystemClock.elapsedRealtime();
		return now - timeStart != 0 ? (float) (bytesSent - initialBytesSent) / (float) (now - timeStart) : 0.0f;
	}

	int getCurrentPart() {
		return currentPart;
	}

	int getTotalParts() {
		return totalParts;
	}

	boolean isLastPart() {
		return currentPart == totalParts;
	}
}
