package no.nordicsemi.dfu.storage

import android.net.Uri

sealed class FileResource

object LoadingFile : FileResource()
data class FileDownloaded(val uri: Uri) : FileResource()
object FileError : FileResource()
