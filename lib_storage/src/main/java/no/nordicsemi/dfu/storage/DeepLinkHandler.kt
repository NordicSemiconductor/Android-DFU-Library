/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.dfu.storage

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Example test: adb shell am start -W -a android.intent.action.VIEW -d "http://www.nordicsemi.com/dfu/?file=https://drive.google.com/uc?export=download%26id=1EcPZBA7Mi4-g-ygnk1SnTiSm0r57i1Rt" no.nordicsemi.android.dfu
 * '&' in nested link encode with '%26'
 */

private const val PARAM_KEY = "file"

@Singleton
class DeepLinkHandler @Inject internal constructor(
    private val downloadManagerWrapper: ExternalFileDataSource,
) {

    private val _zipFile = MutableStateFlow<Uri?>(null)
    val zipFile = _zipFile.asStateFlow()

    /**
     * @return true if deep link was handled
     */
    fun handleDeepLink(intent: Intent?): Boolean {
        val data = intent?.data
        val deeplinkParam = data?.getQueryParameter(PARAM_KEY)
        if (deeplinkParam != null) {
            downloadManagerWrapper.download(deeplinkParam)
            return true
        } else if (data != null) {
            _zipFile.value = data
            return true
        }
        return false
    }
}
