package no.nordicsemi.dfu.profile.view

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.data.*
import no.nordicsemi.dfu.profile.view.components.DFUSelectDeviceView
import no.nordicsemi.dfu.profile.view.components.DFUSelectFileView
import no.nordicsemi.dfu.profile.viewmodel.DFUViewModel
import no.nordicsemi.ui.scanner.ui.exhaustive

@Composable
fun DFUScreen() {
    val viewModel: DFUViewModel = hiltViewModel()
    val state = viewModel.state.collectAsState().value
    val onEvent: (DFUViewEvent) -> Unit = { viewModel.onEvent(it) }

    Log.d("AAATESTAAA", "state: $state")

    Column {
        DFUAppBar()
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Column(modifier = Modifier.padding(16.dp)) {
                DFUSelectFileView(state.fileViewEntity, onEvent)

                Spacer(modifier = Modifier.size(16.dp))

                DFUSelectDeviceView(state.deviceViewEntity, onEvent)
            }
        }
//        DFUSelectMainFileView(state, onEvent)
    }

//    Column {
//        DFUAppBar()
//
//        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
//            Column(modifier = Modifier.padding(16.dp)) {
//                when (state) {
//                    is ReadFileState -> DFUSelectMainFileView(state, onEvent)
//                    is FileSummaryState -> DFUSummaryView(state, onEvent)
//                    is WorkingState -> WorkingStateView(state)
//                }.exhaustive
//            }
//        }
//    }
}

//@Composable
//private fun WorkingStateView(state: WorkingState) {
//    val viewModel: DFUViewModel = hiltViewModel()
//
//    when (state.result) {
//        is WorkingStatus -> WorkingStatusView(state.result)
//        IdleStatus -> {
//            Text(text = "Hello, World!")
//        }
//    }
//}

@Composable
private fun WorkingStatusView(status: WorkingStatus) {
    val viewModel: DFUViewModel = hiltViewModel()
    val onEvent: (DFUViewEvent) -> Unit = { viewModel.onEvent(NavigateUp) }

    when (status.status) {
        Connecting -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_connecting))
        Connected -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_connected))
        Started -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_started))
        Starting -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_starting))
        Validating -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_validating))
        Aborted -> DFUErrorView(onEvent)
        Completed -> DFUSuccessView(onEvent)
        is ProgressUpdate -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_progress_update, status.status.progress))
        EnablingDfu -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_enabling))
        is Error -> DFUErrorView(onEvent)
        Disconnected -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_disconnected))
        Disconnecting -> DFUInstallingView(stringResource(id = R.string.dfu_display_status_disconnecting))
    }.exhaustive
}

@Composable
private fun DFUAppBar() {
    SmallTopAppBar(
        title = { Text(stringResource(id = R.string.dfu_title)) },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = colorResource(id = R.color.appBarColor),
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    )
}
