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
