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

import android.bluetooth.BluetoothGatt;
import android.content.Intent;

import no.nordicsemi.android.dfu.internal.exception.DeviceDisconnectedException;
import no.nordicsemi.android.dfu.internal.exception.DfuException;
import no.nordicsemi.android.dfu.internal.exception.UploadAbortedException;

/* package */ class DfuServiceProvider implements DfuCallback {
	private BaseDfuImpl mImpl;
	private boolean mPaused;
	private boolean mAborted;

	/* package */ DfuService getServiceImpl(final Intent intent, final DfuBaseService service, final BluetoothGatt gatt) throws DfuException, DeviceDisconnectedException, UploadAbortedException {
		try {
			mImpl = new ButtonlessDfuWithBondSharingImpl(intent, service);
			if (mImpl.isClientCompatible(intent, gatt))
				return mImpl;

			mImpl = new ButtonlessDfuWithoutBondSharingImpl(intent, service);
			if (mImpl.isClientCompatible(intent, gatt))
				return mImpl;

			mImpl = new SecureDfuImpl(intent, service);
			if (mImpl.isClientCompatible(intent, gatt))
				return mImpl;

			mImpl = new LegacyButtonlessDfuImpl(intent, service); // This will read the DFU Version char...
			if (mImpl.isClientCompatible(intent, gatt))
				return mImpl;

			mImpl = new LegacyDfuImpl(intent, service);           // ...that this impl will then use.
			if (mImpl.isClientCompatible(intent, gatt))
				return mImpl;

			// Support for experimental Buttonless DFU Service from SDK 12. This feature must be explicitly enabled in the initiator.
			final boolean enableUnsafeExperimentalButtonlessDfuService = intent.getBooleanExtra(DfuBaseService.EXTRA_UNSAFE_EXPERIMENTAL_BUTTONLESS_DFU, false);
			if (enableUnsafeExperimentalButtonlessDfuService) {
				mImpl = new ExperimentalButtonlessDfuImpl(intent, service);
				if (mImpl.isClientCompatible(intent, gatt))
					return mImpl;
			}
			// No implementation found
			return null;
		} finally {
			// Call pause() or abort() only on the chosen implementation
			if (mImpl != null) {
				if (mPaused)
					mImpl.pause();
				if (mAborted)
					mImpl.abort();
			}
		}
	}

	@Override
	public DfuGattCallback getGattCallback() {
		return mImpl != null ? mImpl.getGattCallback() : null;
	}

	@Override
	public void onBondStateChanged(final int state) {
		if (mImpl != null)
			mImpl.onBondStateChanged(state);
	}

	@Override
	public void pause() {
		mPaused = true;
	}

	@Override
	public void resume() {
		mPaused = false;
	}

	@Override
	public void abort() {
		mAborted = true;
		if (mImpl != null)
			mImpl.abort();
	}
}
