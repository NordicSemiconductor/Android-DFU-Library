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

/* package */ class DfuProgressInfo {
	private int progress;
	private int bytesSent;
	private int bytesReceived;
	private int imageSizeInBytes;
	private int maxObjectSizeInBytes;
	private int currentPart;
	private int totalParts;

	public DfuProgressInfo(final int imageSizeInBytes) {
		this.imageSizeInBytes = imageSizeInBytes;
		this.maxObjectSizeInBytes = Integer.MAX_VALUE; // by default the whole firmware will be sent as a single object
	}

	public DfuProgressInfo setPart(final int currentPart, final int totalParts) {
		this.currentPart = currentPart;
		this.totalParts = totalParts;
		return this;
	}

	public DfuProgressInfo setProgress(final int progress) {
		this.progress = progress;
		return this;
	}

	public DfuProgressInfo setBytesSent(final int bytesSent) {
		this.bytesSent = bytesSent;
		this.progress = (int) (100.0f * bytesSent / imageSizeInBytes);
		return this;
	}

	public DfuProgressInfo addBytesSent(final int increment) {
		return setBytesSent(bytesSent + increment);
	}

	public DfuProgressInfo setBytesReceived(final int bytesReceived) {
		this.bytesReceived = bytesReceived;
		return this;
	}

	public DfuProgressInfo addBytesReceived(final int increment) {
		return setBytesReceived(bytesReceived + increment);
	}

	public void setMaxObjectSizeInBytes(final int bytes) {
		this.maxObjectSizeInBytes = bytes;
	}

	public boolean isComplete() {
		return bytesSent == imageSizeInBytes;
	}

	public boolean isObjectComplete() {
		return (bytesSent % maxObjectSizeInBytes) == 0;
	}

	public int getAvailableObjectSizeIsBytes() {
		return maxObjectSizeInBytes - (bytesSent % maxObjectSizeInBytes);
	}

	public int getProgress() {
		return progress;
	}

	public int getBytesSent() {
		return bytesSent;
	}

	public int getBytesReceived() {
		return bytesReceived;
	}

	public int getImageSizeInBytes() {
		return imageSizeInBytes;
	}

	public int getCurrentPart() {
		return currentPart;
	}

	public int getTotalParts() {
		return totalParts;
	}
}
