package no.nordicsemi.dfu

import no.nordicsemi.android.navigation.ComposeDestination
import no.nordicsemi.android.navigation.ComposeDestinations
import no.nordicsemi.dfu.profile.view.DFUScreen

val HomeDestinations = ComposeDestinations(HomeDestination.values().map { it.destination })

enum class HomeDestination(val destination: ComposeDestination) {
    HOME(ComposeDestination("home-destination") { DFUScreen() })
}
