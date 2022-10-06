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

package no.nordicsemi.android.dfu.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import no.nordicsemi.android.common.navigation.NavigationView
import no.nordicsemi.android.common.theme.NordicActivity
import no.nordicsemi.android.common.theme.NordicTheme
import no.nordicsemi.android.dfu.analytics.DfuAnalytics
import no.nordicsemi.android.dfu.analytics.HandleDeepLinkEvent
import no.nordicsemi.android.dfu.profile.DFUDestinations
import no.nordicsemi.android.dfu.profile.scanner.ScannerDestinations
import no.nordicsemi.android.dfu.storage.DeepLinkHandler
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : NordicActivity() {

    @Inject
    lateinit var linkHandler: DeepLinkHandler

    @Inject
    lateinit var analytics: DfuAnalytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (linkHandler.handleDeepLink(intent)) {
            analytics.logEvent(HandleDeepLinkEvent)
        }

        setContent {
            NordicTheme {
                NavigationView(DFUDestinations + ScannerDestinations)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (linkHandler.handleDeepLink(intent)) {
            analytics.logEvent(HandleDeepLinkEvent)
        }
    }
}
