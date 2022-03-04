package no.nordicsemi.dfu.profile.settings.view

sealed interface SettingsScreenViewEvent

object OnPacketsReceiptNotificationSwitchClick : SettingsScreenViewEvent

object OnKeepBondInformationSwitchClick : SettingsScreenViewEvent

object OnExternalMcuDfuSwitchClick : SettingsScreenViewEvent

object OnAboutAppClick : SettingsScreenViewEvent

object NavigateUp : SettingsScreenViewEvent
