package no.nordicsemi.dfu.profile.main.view

import android.net.Uri

internal sealed class DFUViewEvent

internal data class OnZipFileSelected(val file: Uri) : DFUViewEvent()

internal object OnInstallButtonClick : DFUViewEvent()

internal object OnCloseButtonClick : DFUViewEvent()

internal object OnAbortButtonClick : DFUViewEvent()

internal object OnSelectDeviceButtonClick : DFUViewEvent()

internal object OnDisconnectButtonClick : DFUViewEvent()

internal object NavigateUp : DFUViewEvent()

internal object OnSettingsButtonClick : DFUViewEvent()

internal object OnLoggerButtonClick : DFUViewEvent()
