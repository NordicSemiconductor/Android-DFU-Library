package no.nordicsemi.android.dfu.profile.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.common.navigation.NavigationManager
import no.nordicsemi.android.common.permission.view.PermissionViewModel
import no.nordicsemi.android.common.ui.scanner.ScannerResultCancel
import no.nordicsemi.android.common.ui.scanner.ScannerResultSuccess
import no.nordicsemi.android.common.ui.scanner.ScannerScreen

@Composable
fun ScannerContent(navigationManager: NavigationManager) {
    val viewModel = hiltViewModel<PermissionViewModel>()
    val isLocationPermissionRequired = viewModel.isLocationPermissionRequired.collectAsState().value

    ScannerScreen(
        uuid = null,
        isLocationPermissionRequired = isLocationPermissionRequired,
        onResult = {
            when (it) {
                ScannerResultCancel -> navigationManager.navigateUp()
                is ScannerResultSuccess -> navigationManager.navigateUp(
                    ScannerResult(
                        ScannerDestinationId,
                        it.device
                    )
                )
            }
        },
        onDevicesDiscovered = { viewModel.onDevicesDiscovered() }
    )
}