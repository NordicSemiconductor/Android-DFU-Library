package no.nordicsemi.dfu.profile.settings.view

sealed interface SettingsScreenViewEvent

object OnPacketsReceiptNotificationSwitchClick : SettingsScreenViewEvent

data class OnNumberOfPocketsChange(val numberOfPockets: Int) : SettingsScreenViewEvent

object OnDisableResumeSwitchClick : SettingsScreenViewEvent

object OnForceScanningAddressesSwitchClick : SettingsScreenViewEvent

object OnKeepBondInformationSwitchClick : SettingsScreenViewEvent

object OnExternalMcuDfuSwitchClick : SettingsScreenViewEvent

object OnAboutAppClick : SettingsScreenViewEvent

object OnShowWelcomeClick : SettingsScreenViewEvent

object NavigateUp : SettingsScreenViewEvent
