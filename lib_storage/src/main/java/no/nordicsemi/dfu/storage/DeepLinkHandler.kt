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
