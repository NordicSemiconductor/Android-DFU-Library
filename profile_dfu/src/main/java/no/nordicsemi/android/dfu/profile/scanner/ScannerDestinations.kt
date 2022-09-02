package no.nordicsemi.android.dfu.profile.scanner

import no.nordicsemi.android.common.navigation.ComposeDestination
import no.nordicsemi.android.common.navigation.ComposeDestinations
import no.nordicsemi.android.common.navigation.DestinationId
import no.nordicsemi.android.common.navigation.NavigationResult
import no.nordicsemi.android.common.ui.scanner.model.DiscoveredBluetoothDevice

val ScannerDestinationId = DestinationId("uiscanner-destination")

private val ScannerDestination =
    ComposeDestination(ScannerDestinationId) { navigationManager ->
        ScannerContent(navigationManager = navigationManager)
    }

val ScannerDestinations = ComposeDestinations(listOf(ScannerDestination))

data class ScannerResult(
    override val destinationId: DestinationId,
    val device: DiscoveredBluetoothDevice
) : NavigationResult