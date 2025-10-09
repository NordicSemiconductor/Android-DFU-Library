package no.nordicsemi.android.dfu.profile.scanner.view

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.common.navigation.viewmodel.SimpleNavigationViewModel
import no.nordicsemi.android.common.scanner.DeviceSelected
import no.nordicsemi.android.common.scanner.ScannerScreen
import no.nordicsemi.android.common.scanner.ScanningCancelled
import no.nordicsemi.android.dfu.profile.scanner.Scanner
import no.nordicsemi.android.dfu.profile.scanner.data.DfuTarget

@Composable
internal fun ScannerContent() {
    val vm: SimpleNavigationViewModel = hiltViewModel()

    ScannerScreen(
        cancellable = true,
        onResultSelected = { result ->
            when (result) {
                ScanningCancelled -> vm.navigateUp()
                is DeviceSelected -> vm.navigateUpWithResult(
                    from = Scanner,
                    result = DfuTarget(
                        result = result.scanResult.peripheral,
                        name = result.scanResult.advertisingData.name ?: result.scanResult.peripheral.name
                    )
                )
            }
        },
    )
}