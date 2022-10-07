package no.nordicsemi.android.dfu.profile.scanner.view

import androidx.compose.runtime.Composable
import no.nordicsemi.android.common.navigation.NavigationManager
import no.nordicsemi.android.common.ui.scanner.DeviceSelected
import no.nordicsemi.android.common.ui.scanner.ScannerScreen
import no.nordicsemi.android.common.ui.scanner.ScanningCancelled
import no.nordicsemi.android.dfu.profile.scanner.ScannerDestination
import no.nordicsemi.android.dfu.profile.scanner.ScannerResult
import no.nordicsemi.android.dfu.profile.scanner.data.DfuTarget

@Composable
internal fun ScannerContent(navigationManager: NavigationManager) {
    ScannerScreen(
        uuid = null,
        onResult = { result ->
            when (result) {
                ScanningCancelled -> navigationManager.navigateUp()
                is DeviceSelected -> navigationManager.navigateUp(
                    ScannerResult(ScannerDestination, DfuTarget(result))
                )
            }
        },
    )
}