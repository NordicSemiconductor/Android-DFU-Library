package no.nordicsemi.dfu.profile.main.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.main.viewmodel.DFUViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DFUScreen() {
    val viewModel: DFUViewModel = hiltViewModel()
    val state = viewModel.state.collectAsState().value
    val onEvent: (DFUViewEvent) -> Unit = { viewModel.onEvent(it) }

    Column {
        DFUAppBar(onEvent)
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedCard(
                modifier = Modifier.padding(16.dp),
            ) {
                DFUSelectFileView(state.isRunning(), state.fileViewEntity, onEvent)

                DFUSelectedDeviceView(state.isRunning(), state.deviceViewEntity, onEvent)

                DFUProgressView(state.progressViewEntity, onEvent)

                Spacer(modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun DFUAppBar(onEvent: (DFUViewEvent) -> Unit) {
    SmallTopAppBar(
        title = { Text(stringResource(id = R.string.dfu_title)) },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = colorResource(id = R.color.appBarColor),
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        actions = {
            IconButton(onClick = { onEvent(OnLoggerButtonClick) }) {
                Icon(
                    painterResource(id = R.drawable.ic_logger),
                    contentDescription = stringResource(id = R.string.open_logger),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(onClick = { onEvent(OnSettingsButtonClick) }) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(id = R.string.dfu_settings_action)
                )
            }
        }
    )
}
