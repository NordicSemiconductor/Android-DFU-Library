package no.nordicsemi.android.dfu.profile.scanner

import androidx.compose.runtime.Composable
import no.nordicsemi.android.common.navigation.NavigationManager
import no.nordicsemi.android.common.ui.scanner.DeviceSelected
import no.nordicsemi.android.common.ui.scanner.ScannerScreen
import no.nordicsemi.android.common.ui.scanner.ScanningCancelled

@Composable
fun ScannerContent(navigationManager: NavigationManager) {
    ScannerScreen(
        uuid = null,
        onResult = { result ->
            when (result) {
                ScanningCancelled -> navigationManager.navigateUp()
                is DeviceSelected -> navigationManager.navigateUp(
                    ScannerResult(ScannerDestinationId, result.device)
                )
            }
        },
    )
}