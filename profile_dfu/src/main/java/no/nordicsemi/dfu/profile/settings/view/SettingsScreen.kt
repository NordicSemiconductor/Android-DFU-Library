package no.nordicsemi.dfu.profile.settings.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.material.you.CheckboxFallback
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.settings.viewmodel.SettingsViewModel

@Composable
internal fun SettingsScreen() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state = viewModel.state.collectAsState().value
    val onEvent: (SettingsScreenViewEvent) -> Unit = { viewModel.onEvent(it) }

    Column {
        SettingsAppBar(onEvent)

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_packets_receipt_notification),
            state.packetsReceiptNotification
        ) {
            onEvent(OnPacketsReceiptNotificationSwitchClick)
        }

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_keep_bond_information),
            state.keepBondInformation
        ) {
            onEvent(OnKeepBondInformationSwitchClick)
        }

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_external_mcu_dfu),
            state.externalMcuDfu
        ) {
            onEvent(OnExternalMcuDfuSwitchClick)
        }
    }
}

@Composable
private fun SettingsAppBar(onEvent: (SettingsScreenViewEvent) -> Unit) {
    SmallTopAppBar(
        title = { Text(stringResource(id = R.string.dfu_settings)) },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = colorResource(id = R.color.appBarColor),
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        navigationIcon = {
            IconButton(onClick = { onEvent(NavigateUp) }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.dfu_navigate_up)
                )
            }
        }
    )
}

@Composable
private fun SwitchSettingsComponent(text: String, isChecked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )

        CheckboxFallback(checked = isChecked, onCheckedChange = { onClick() })
    }
}
