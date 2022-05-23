package no.nordicsemi.dfu.storage

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
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

    private val _fileResource = MutableStateFlow<FileResource?>(null)
    val fileResource = _fileResource.asStateFlow()

    private val downloadManger = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private var downloadID: Long? = null

    private val onDownloadCompleteReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {
            val id: Long = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)

            if (downloadID == id) {
                _fileResource.value = downloadManger.getUriForDownloadedFile(id)?.let {
                    FileDownloaded(it)
                } ?: FileError
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
        _fileResource.value = LoadingFile

        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setTitle(parser.parseName(url))
        request.setDescription(context.getString(R.string.storage_notification_description))

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            request.allowScanningByMediaScanner()
        }
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, parser.parseName(url))

        downloadID = downloadManger.enqueue(request)
    }

    private fun isRunning(): Boolean {
        return fileResource.value is LoadingFile
    }
}
