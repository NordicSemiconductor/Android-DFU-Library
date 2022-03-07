package no.nordicsemi.dfu.storage

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExternalFileDataSource @Inject internal constructor(
    @ApplicationContext
    private val context: Context,
    private val parser: FileNameParser
) {

    private val _uri = MutableStateFlow<Uri?>(null)
    val uri = _uri.asStateFlow()

    private val downloadManger = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private var downloadID: Long? = null

    private val onDownloadCompleteReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            val id: Long = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (downloadID == id) {
                val uri = downloadManger.getUriForDownloadedFile(id)
                _uri.value = uri
                downloadID = null
            }
        }
    }

    init {
        context.registerReceiver(
            onDownloadCompleteReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    fun download(url: String) {
        if (isRunning()) {
            return
        }

        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setTitle(parser.parseName(url))
        request.setDescription(context.getString(R.string.storage_notification_description))

        request.allowScanningByMediaScanner()
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, parser.parseName(url))

        downloadID = downloadManger.enqueue(request)
    }

    private fun isRunning() = downloadID != null
}
