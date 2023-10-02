package no.nordicsemi.android.dfu.profile.scanner.view

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.common.navigation.viewmodel.SimpleNavigationViewModel
import no.nordicsemi.android.dfu.profile.scanner.Scanner
import no.nordicsemi.android.dfu.profile.scanner.data.DfuTarget
import no.nordicsemi.android.kotlin.ble.ui.scanner.DeviceSelected
import no.nordicsemi.android.kotlin.ble.ui.scanner.ScannerScreen
import no.nordicsemi.android.kotlin.ble.ui.scanner.ScanningCancelled

@Composable
internal fun ScannerContent() {
    val vm: SimpleNavigationViewModel = hiltViewModel()

    ScannerScreen(
        uuid = null,
        onResult = { result ->
            when (result) {
                ScanningCancelled -> vm.navigateUp()
                is DeviceSelected -> vm.navigateUpWithResult(Scanner, DfuTarget(result.scanResults.device))
            }
        },
    )
}