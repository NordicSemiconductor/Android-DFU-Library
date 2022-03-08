package no.nordicsemi.dfu.storage

import android.content.Intent
import javax.inject.Inject

/**
 * Example test: adb shell am start -W -a android.intent.action.VIEW -d "http://www.nordicsemi.com/dfu/?file=https://drive.google.com/uc?export=download%26id=1EcPZBA7Mi4-g-ygnk1SnTiSm0r57i1Rt" no.nordicsemi.android.dfu
 * '&' in nested link encode with '%26'
 */

private const val PARAM_KEY = "file"

class DeepLinkHandler @Inject internal constructor(
    private val downloadManagerWrapper: ExternalFileDataSource
) {

    fun handleDeepLink(intent: Intent?) {
        intent?.data?.getQueryParameter(PARAM_KEY)?.let {
            downloadManagerWrapper.download(it)
        }
    }
}
