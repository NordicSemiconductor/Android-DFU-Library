package no.nordicsemi.dfu

import no.nordicsemi.android.navigation.ComposeDestination
import no.nordicsemi.android.navigation.ComposeDestinations
import no.nordicsemi.dfu.view.HomeScreen
import no.nordicsemi.ui.scanner.navigation.view.FindDeviceScreen

val HomeDestinations = ComposeDestinations(HomeDestination.values().map { it.destination })

enum class HomeDestination(val destination: ComposeDestination) {
    HOME(ComposeDestination("home-destination") { HomeScreen() }),
    SCANNER(ComposeDestination("scanner-destination") { FindDeviceScreen() });
}
