/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.dfu.profile.settings.view

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.common.ui.view.NordicAppBar
import no.nordicsemi.android.dfu.BuildConfig.VERSION_CODE
import no.nordicsemi.android.dfu.BuildConfig.VERSION_NAME
import no.nordicsemi.android.dfu.profile.settings.R
import no.nordicsemi.android.dfu.profile.settings.view.dialog.NumberOfPocketsDialog
import no.nordicsemi.android.dfu.profile.settings.view.widget.Headline
import no.nordicsemi.android.dfu.profile.settings.view.widget.SettingsButton
import no.nordicsemi.android.dfu.profile.settings.view.widget.SettingsSwitch
import no.nordicsemi.android.dfu.profile.settings.view.widget.SettingsTimeSlider
import no.nordicsemi.android.dfu.settings.domain.DFUSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    state: DFUSettings,
    onEvent: (SettingsScreenViewEvent) -> Unit,
    modifier: Modifier = Modifier,
    other: @Composable ColumnScope.() -> Unit = {},
) {
    var showDialog by rememberSaveable { mutableStateOf(false) }

    if (showDialog) {
        NumberOfPocketsDialog(state.numberOfPackets,
            onDismiss = { showDialog = false },
            onNumberOfPocketsChange = { onEvent(OnNumberOfPocketsChange(it)) }
        )
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        NordicAppBar(
            title = { Text(text = stringResource(R.string.dfu_settings)) },
            onNavigationButtonClick = { onEvent(NavigateUp) },
            actions = {
                IconButton(onClick = { onEvent(OnResetButtonClick) }) {
                    Icon(
                        imageVector = Icons.Outlined.SettingsBackupRestore,
                        contentDescription = stringResource(id = R.string.dfu_settings_reset)
                    )
                }
            }
        )

        // Scrollable Column
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 16.dp)
                .sizeIn(maxWidth = 600.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_packets_receipt_notification),
                description = stringResource(id = R.string.dfu_settings_packets_receipt_notification_info),
                isChecked = state.packetsReceiptNotification,
                onClick = { onEvent(OnPacketsReceiptNotificationSwitchClick) }
            )

            SettingsButton(
                text = stringResource(id = R.string.dfu_settings_number_of_pockets),
                description = state.numberOfPackets.toString(),
                onClick = { showDialog = true },
                enabled = state.packetsReceiptNotification,
            )

            SettingsTimeSlider(
                text = stringResource(id = R.string.dfu_settings_reboot_time),
                description = stringResource(id = R.string.dfu_settings_reboot_time_info),
                value = state.rebootTime,
                valueRange = 0..5_000,
                stepInMilliseconds = 1_000, // 1 second
                onChange = { onEvent(OnRebootTimeChange(it)) }
            )

            SettingsTimeSlider(
                text = stringResource(id = R.string.dfu_settings_scan_timeout),
                description = stringResource(id = R.string.dfu_settings_scan_timeout_info),
                value = state.scanTimeout,
                valueRange = 1_000..10_000,
                stepInMilliseconds = 1_000, // 1 second
                onChange = { onEvent(OnScanTimeoutChange(it)) }
            )

            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_mtu),
                description = stringResource(id = R.string.dfu_settings_mtu_info),
                isChecked = state.mtuRequestEnabled,
                onClick = { onEvent(OnMtuRequestClick) }
            )

            Headline(stringResource(id = R.string.dfu_settings_headline_secure_dfu))

            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_disable_resume),
                description = stringResource(id = R.string.dfu_settings_disable_resume_info),
                isChecked = state.disableResume,
                onClick = { onEvent(OnDisableResumeSwitchClick) }
            )
            
            SettingsTimeSlider(
                text = stringResource(id = R.string.dfu_settings_prepare_data_object_delay),
                description = stringResource(id = R.string.dfu_settings_prepare_data_object_delay_info),
                value = state.prepareDataObjectDelay,
                valueRange = 0..500,
                stepInMilliseconds = 100, // 0.1 seconds
                onChange = { onEvent(OnPrepareDataObjectDelayChange(it)) }
            )

            Headline(stringResource(id = R.string.dfu_settings_headline_legacy_dfu))

            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_force_scanning),
                description = stringResource(id = R.string.dfu_settings_force_scanning_info),
                isChecked = state.forceScanningInLegacyDfu,
                onClick = { onEvent(OnForceScanningAddressesSwitchClick) }
            )

            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_keep_bond_information),
                description = stringResource(id = R.string.dfu_settings_keep_bond_information_info),
                isChecked = state.keepBondInformation,
                onClick = { onEvent(OnKeepBondInformationSwitchClick) }
            )

            SettingsSwitch(
                text = stringResource(id = R.string.dfu_settings_external_mcu_dfu),
                description = stringResource(id = R.string.dfu_settings_external_mcu_dfu_info),
                isChecked = state.externalMcuDfu,
                onClick = { onEvent(OnExternalMcuDfuSwitchClick) }
            )

            Headline(stringResource(id = R.string.dfu_settings_other))

            SettingsButton(
                text = stringResource(id = R.string.dfu_about_app),
                onClick = { onEvent(OnAboutAppClick) }
            )

            SettingsButton(
                text = stringResource(id = R.string.dfu_about_dfu),
                description = stringResource(id = R.string.dfu_about_dfu_desc),
                onClick = { onEvent(OnAboutDfuClick) }
            )

            other()

            Text(
                text = stringResource(id = R.string.dfu_version, VERSION_NAME, VERSION_CODE),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelMedium,
                color = LocalContentColor.current.copy(alpha = 0.38f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.size(16.dp))
        }
    }
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        state = DFUSettings(),
        onEvent = {}
    )
}
