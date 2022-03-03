package no.nordicsemi.dfu.profile

import no.nordicsemi.android.navigation.ComposeDestination
import no.nordicsemi.android.navigation.ComposeDestinations
import no.nordicsemi.android.navigation.DestinationId
import no.nordicsemi.dfu.profile.main.view.DFUScreen
import no.nordicsemi.dfu.profile.settings.view.SettingsScreen

val DfuMainScreen = DestinationId("dfu-main-screen")
internal val DfuSettingsScreen = DestinationId("dfu-settings-screen")

private val destinations = listOf(
    ComposeDestination(DfuMainScreen) { DFUScreen() },
    ComposeDestination(DfuSettingsScreen) { SettingsScreen() },
)

val DFUDestinations = ComposeDestinations(destinations)
