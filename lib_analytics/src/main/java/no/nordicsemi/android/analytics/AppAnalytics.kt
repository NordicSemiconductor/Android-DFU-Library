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

package no.nordicsemi.android.analytics

import android.annotation.SuppressLint
import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("MissingPermission")
@Singleton
class AppAnalytics @Inject constructor(
    @ApplicationContext
    private val context: Context
) {

    private val firebase by lazy { FirebaseAnalytics.getInstance(context) }

    fun logEvent(event: AppEvent) {
        when (event) {
            AppOpenEvent,
            HandleDeepLinkEvent,
            DFUSuccessEvent -> firebase.logEvent(event.eventName, null)
            DeviceSelectedEvent -> firebase.logEvent(event.eventName, null)
            FileSelectedEvent -> firebase.logEvent(event.eventName, null)
            InstallationStartedEvent -> firebase.logEvent(event.eventName, null)
            is DFUErrorEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is DisableResumeSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is ExternalMCUSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is ForceScanningSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is KeepBondSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is NumberOfPacketsSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
            is PacketsReceiptNotificationSettingsEvent -> firebase.logEvent(event.eventName, event.createBundle())
        }
    }
}
