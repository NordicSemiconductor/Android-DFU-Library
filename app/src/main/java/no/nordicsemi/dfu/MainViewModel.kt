package no.nordicsemi.dfu

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import no.nordicsemi.android.analytics.AppAnalytics
import no.nordicsemi.android.analytics.HandleDeepLinkEvent
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val analytics: AppAnalytics
) : ViewModel() {

    fun logEvent(isDeeplink: Boolean) {
        if (isDeeplink) {
            analytics.logEvent(HandleDeepLinkEvent)
        }
    }
}
