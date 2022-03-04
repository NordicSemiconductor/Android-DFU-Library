package no.nordicsemi.dfu.profile

import no.nordicsemi.android.navigation.ComposeDestination
import no.nordicsemi.android.navigation.ComposeDestinations
import no.nordicsemi.android.navigation.DestinationId
import no.nordicsemi.dfu.profile.main.view.DFUScreen
import no.nordicsemi.dfu.profile.settings.view.SettingsScreen
import no.nordicsemi.dfu.profile.welcome.view.WelcomeScreen

val DfuMainScreen = DestinationId("dfu-main-screen")
internal val DfuSettingsScreen = DestinationId("dfu-settings-screen")
internal val DfuWelcomeScreen = DestinationId("dfu-welcome-screen")

private val destinations = listOf(
    ComposeDestination(DfuMainScreen) { DFUScreen() },
    ComposeDestination(DfuSettingsScreen) { SettingsScreen() },
    ComposeDestination(DfuWelcomeScreen) { WelcomeScreen() },
)

val DFUDestinations = ComposeDestinations(destinations)
