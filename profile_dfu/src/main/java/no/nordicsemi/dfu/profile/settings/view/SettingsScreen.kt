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

package no.nordicsemi.dfu.profile.settings.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.dfu.BuildConfig.VERSION_NAME
import no.nordicsemi.android.theme.CheckboxFallback
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.settings.viewmodel.SettingsViewModel

@Composable
internal fun SettingsScreen() {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val state = viewModel.state.collectAsState().value
    val onEvent: (SettingsScreenViewEvent) -> Unit = { viewModel.onEvent(it) }
    val showDialog = rememberSaveable { mutableStateOf(false) }

    if (showDialog.value) {
        NumberOfPocketsDialog(state.numberOfPackets, onDismiss = { showDialog.value = false }) {
            onEvent(it)
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SettingsAppBar(onEvent)

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_packets_receipt_notification),
            stringResource(id = R.string.dfu_settings_packets_receipt_notification_info),
            state.packetsReceiptNotification
        ) {
            onEvent(OnPacketsReceiptNotificationSwitchClick)
        }

        if (state.packetsReceiptNotification) {
            SettingsButton(
                stringResource(id = R.string.dfu_settings_number_of_pockets),
                state.numberOfPackets.toString()
            ) {
                showDialog.value = true
            }
        } else {
            DisabledSettingsButton(
                stringResource(id = R.string.dfu_settings_number_of_pockets),
                state.numberOfPackets.toString()
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        Headline(stringResource(id = R.string.dfu_settings_headline_secure_dfu))

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_disable_resume),
            stringResource(id = R.string.dfu_settings_disable_resume_info),
            state.disableResume
        ) {
            onEvent(OnDisableResumeSwitchClick)
        }

        Spacer(modifier = Modifier.size(32.dp))

        Headline(stringResource(id = R.string.dfu_settings_headline_legacy_dfu))

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_force_scanning),
            stringResource(id = R.string.dfu_settings_force_scanning_info),
            state.forceScanningInLegacyDfu
        ) {
            onEvent(OnForceScanningAddressesSwitchClick)
        }

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_keep_bond_information),
            stringResource(id = R.string.dfu_settings_keep_bond_information_info),
            state.keepBondInformation
        ) {
            onEvent(OnKeepBondInformationSwitchClick)
        }

        SwitchSettingsComponent(
            stringResource(id = R.string.dfu_settings_external_mcu_dfu),
            stringResource(id = R.string.dfu_settings_external_mcu_dfu_info),
            state.externalMcuDfu
        ) {
            onEvent(OnExternalMcuDfuSwitchClick)
        }

        Spacer(modifier = Modifier.size(16.dp))

        Headline(stringResource(id = R.string.dfu_settings_other))

        SettingsButton(
            stringResource(id = R.string.dfu_about_app),
            stringResource(id = R.string.dfu_about_app_desc)
        ) {
            onEvent(OnAboutAppClick)
        }

        SettingsButton(
            stringResource(id = R.string.dfu_show_welcome_screen),
        ) {
            onEvent(OnShowWelcomeClick)
        }

        Text(
            text = stringResource(id = R.string.dfu_version, VERSION_NAME),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.size(16.dp))
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
private fun Headline(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.secondary
    )
}

@Composable
private fun SwitchSettingsComponent(
    text: String,
    description: String?,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
            )

            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        CheckboxFallback(checked = isChecked, onCheckedChange = { onClick() })
    }
}

@Composable
private fun SettingsButton(title: String, description: String? = null, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
        )

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DisabledSettingsButton(title: String, description: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.surfaceVariant
        )

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}

@Composable
private fun NumberOfPocketsDialog(
    numberOfPockets: Int,
    onDismiss: () -> Unit,
    onEvent: (SettingsScreenViewEvent) -> Unit
) {
    var numberOfPocketsState by rememberSaveable { mutableStateOf("$numberOfPockets") }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.dfu_settings_number_of_pockets))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = numberOfPocketsState,
                    onValueChange = { newValue ->
                        val value = newValue.toIntOrNull()
                        if (value != null) {
                            numberOfPocketsState = "$value"
                            showError = false
                        } else {
                            numberOfPocketsState = ""
                            showError = true
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(text = stringResource(id = R.string.dfu_settings_number_of_pockets)) },
                )
                if (showError) {
                    Text(text = stringResource(id = R.string.dfu_parse_int_error))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onEvent(OnNumberOfPocketsChange(numberOfPocketsState.toInt()))
                },
                enabled = !showError
            ) {
                Text(text = stringResource(id = R.string.dfu_macro_dialog_confirm))
            }
        },
        dismissButton = {
            Button(onClick = { onDismiss() }) {
                Text(text = stringResource(id = R.string.dfu_macro_dialog_dismiss))
            }
        }
    )
}
