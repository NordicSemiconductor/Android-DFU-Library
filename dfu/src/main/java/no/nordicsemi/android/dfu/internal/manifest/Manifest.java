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

public class Manifest {
	protected FileInfo application;
	protected FileInfo bootloader;
	protected FileInfo softdevice;
	@SerializedName("softdevice_bootloader")
	protected SoftDeviceBootloaderFileInfo softdeviceBootloader;

	// The following options are available only in Secure DFU and will be sent as application (in a single connection).
	// The service is not aware of sizes of each component in the bin file. This information is hidden in the Init Packet.
	@SerializedName("bootloader_application")
	protected SoftDeviceBootloaderFileInfo bootloaderApplication;
	@SerializedName("softdevice_application")
	protected SoftDeviceBootloaderFileInfo softdeviceApplication;
	@SerializedName("softdevice_bootloader_application")
	protected SoftDeviceBootloaderFileInfo softdeviceBootloaderApplication;

	public FileInfo getApplicationInfo() {
		if (application != null)
			return application;
		// The other parts will be sent together with application, so they may be returned here.
		if (softdeviceApplication != null)
			return softdeviceApplication;
		if (bootloaderApplication != null)
			return bootloaderApplication;
		return softdeviceBootloaderApplication;
	}

	public FileInfo getBootloaderInfo() {
		return bootloader;
	}

	public FileInfo getSoftdeviceInfo() {
		return softdevice;
	}

	public SoftDeviceBootloaderFileInfo getSoftdeviceBootloaderInfo() {
		return softdeviceBootloader;
	}

	public boolean isSecureDfuRequired() {
		// Legacy DFU requires sending firmware type together with Start DFU command.
		// The following options were not supported by the legacy bootloader,
		// but in some implementations they are supported in Secure DFU.
		// In Secure DFU the fw type is provided in the Init packet.
		return bootloaderApplication != null || softdeviceApplication != null || softdeviceBootloaderApplication != null;
	}
}
