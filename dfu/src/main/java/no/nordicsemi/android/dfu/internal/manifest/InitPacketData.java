/*
 * Copyright (c) 2015, Nordic Semiconductor
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

package no.nordicsemi.android.dfu.internal.manifest;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class InitPacketData {
	@SerializedName("packet_version") protected int packetVersion;
	@SerializedName("compression_type") protected int compressionType;
	@SerializedName("application_version") protected long applicationVersion;
	@SerializedName("device_revision") protected int deviceRevision;
	@SerializedName("device_type") protected int deviceType;
	@SerializedName("firmware_crc16") protected int firmwareCRC16;
	@SerializedName("firmware_hash") protected String firmwareHash;
	@SerializedName("softdevice_req") protected List<Integer> softdeviceReq;

	public int getPacketVersion() {
		return packetVersion;
	}

	public int getCompressionType() {
		return compressionType;
	}

	public long getApplicationVersion() {
		return applicationVersion;
	}

	public int getDeviceRevision() {
		return deviceRevision;
	}

	public int getDeviceType() {
		return deviceType;
	}

	public int getFirmwareCRC16() {
		return firmwareCRC16;
	}

	public String getFirmwareHash() {
		return firmwareHash;
	}

	public List<Integer> getSoftdeviceReq() {
		return softdeviceReq;
	}
}
