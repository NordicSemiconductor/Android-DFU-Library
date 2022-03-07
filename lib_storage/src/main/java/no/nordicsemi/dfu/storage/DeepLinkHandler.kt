package no.nordicsemi.dfu.storage

import android.content.Intent
import android.util.Log
import javax.inject.Inject

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
