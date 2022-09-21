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

package no.nordicsemi.android.dfu.profile.main.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.common.core.parseBold
import no.nordicsemi.android.common.theme.view.WizardStepComponent
import no.nordicsemi.android.common.theme.view.WizardStepAction
import no.nordicsemi.android.common.theme.view.WizardStepState
import no.nordicsemi.android.common.ui.scanner.model.DiscoveredBluetoothDevice
import no.nordicsemi.android.dfu.profile.R

internal sealed class DFUSelectDeviceViewEntity

internal object DisabledSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

internal object NotSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

internal data class SelectedDeviceViewEntity(val device: DiscoveredBluetoothDevice) : DFUSelectDeviceViewEntity()

private val icon = Icons.Outlined.Bluetooth

@Composable
internal fun DFUSelectedDeviceView(
    viewEntity: DFUSelectDeviceViewEntity,
    enabled: Boolean,
    onEvent: (DFUViewEvent) -> Unit
) {
    when (viewEntity) {
        DisabledSelectedDeviceViewEntity -> DFUDisabledSelectedDeviceView()
        NotSelectedDeviceViewEntity -> DFUNotSelectedDeviceView(onEvent)
        is SelectedDeviceViewEntity -> DFUSelectedDeviceView(viewEntity, enabled, onEvent)
    }
}

@Composable
internal fun DFUDisabledSelectedDeviceView() {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_device),
        decor = WizardStepAction.Action(stringResource(id = R.string.dfu_select_device), enabled = false),
        state = WizardStepState.INACTIVE,
    ) {
        Text(
            text = stringResource(id = R.string.dfu_select_device_info),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
internal fun DFUNotSelectedDeviceView(onEvent: (DFUViewEvent) -> Unit) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_device),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_select_device),
            onClick = { onEvent(OnSelectDeviceButtonClick) }
        ),
        state = WizardStepState.CURRENT,
    ) {
        Text(
            text = stringResource(id = R.string.dfu_select_device_info),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val DEVICE_NAME = "Name: <b>%s</b>"
private const val DEVICE_ADDRESS = "Address: <b>%s</b>"

@Composable
internal fun DFUSelectedDeviceView(
    viewEntity: SelectedDeviceViewEntity,
    enabled: Boolean,
    onEvent: (DFUViewEvent) -> Unit,
) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_device),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_select_device),
            onClick = { onEvent(OnSelectDeviceButtonClick) },
            enabled = enabled,
        ),
        state = WizardStepState.COMPLETED,
    ) {
        Column {
            Text(
                text = String.format(DEVICE_NAME, viewEntity.device.displayName ?: "No name").parseBold(),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = String.format(DEVICE_ADDRESS, viewEntity.device.address).parseBold(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}