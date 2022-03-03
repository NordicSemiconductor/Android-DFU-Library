package no.nordicsemi.dfu.profile.main.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ZipFile(
    val uri: Uri,
    val name: String,
    val path: String?,
    val size: Long
) : Parcelable
