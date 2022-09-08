package no.nordicsemi.android.dfu.profile.scanner

import no.nordicsemi.android.common.navigation.ComposeDestination
import no.nordicsemi.android.common.navigation.ComposeDestinations
import no.nordicsemi.android.common.navigation.DestinationId
import no.nordicsemi.android.common.navigation.NavigationResult
import no.nordicsemi.android.common.ui.scanner.model.DiscoveredBluetoothDevice

internal val ScannerDestination = DestinationId("uiscanner-destination")

private val destinations = listOf(
    ComposeDestination(ScannerDestination) { navigationManager ->
        ScannerContent(navigationManager = navigationManager)
    }
)

val ScannerDestinations = ComposeDestinations(destinations)

data class ScannerResult(
    override val destinationId: DestinationId,
    val device: DiscoveredBluetoothDevice
) : NavigationResult