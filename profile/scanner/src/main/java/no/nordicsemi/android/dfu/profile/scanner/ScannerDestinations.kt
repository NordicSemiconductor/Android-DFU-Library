package no.nordicsemi.android.dfu.profile.scanner

import no.nordicsemi.android.common.navigation.createDestination
import no.nordicsemi.android.common.navigation.defineDestination
import no.nordicsemi.android.dfu.profile.scanner.data.DfuTarget
import no.nordicsemi.android.dfu.profile.scanner.view.ScannerContent

val Scanner = createDestination<Unit, DfuTarget>("scanner")

val ScannerDestination = defineDestination(Scanner) {
    ScannerContent()
}