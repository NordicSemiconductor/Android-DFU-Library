package no.nordicsemi.dfu.profile.view

import android.net.Uri

internal sealed class DFUViewEvent

internal data class OnZipFileSelected(val file: Uri) : DFUViewEvent()

internal object OnInstallButtonClick : DFUViewEvent()

internal object OnCloseButtonClick : DFUViewEvent()

internal object OnStopButtonClick : DFUViewEvent()

internal object OnDisconnectButtonClick : DFUViewEvent()

internal object NavigateUp : DFUViewEvent()
