package no.nordicsemi.dfu.storage

import javax.inject.Inject

private const val DEFAULT_NAME = "downloaded_dfu_file.zip"
private const val ZIP = "zip"

internal class FileNameParser @Inject constructor() {

    fun parseName(uri: String): String {
        val fileName = uri.split("/").last()

        return if (fileName.lowercase().contains(ZIP)) {
            fileName
        } else {
            DEFAULT_NAME
        }
    }
}
