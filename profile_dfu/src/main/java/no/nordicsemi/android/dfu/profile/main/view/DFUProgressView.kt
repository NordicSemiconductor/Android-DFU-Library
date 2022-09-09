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
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.common.theme.view.*
import no.nordicsemi.android.dfu.profile.R
import no.nordicsemi.android.dfu.profile.main.data.Uploading

private val icon = Icons.Default.Upload

@Composable
internal fun DFUProgressView(viewEntity: DFUProgressViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    when (viewEntity) {
        DisabledProgressViewEntity -> DisabledDFUProgressView()
        is WorkingProgressViewEntity ->
            if (viewEntity.status.isRunning()) {
                DFURunningProgressView(viewEntity.status, onEvent)
            } else if (viewEntity.status.isCompleted()) {
                DFUCompletedProgressView(viewEntity.status)
            } else {
                DFUIdleProgressView(onEvent)
            }
    }
}

@Composable
private fun DisabledDFUProgressView(viewEntity: ProgressItemViewEntity = ProgressItemViewEntity()) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_progress),
        decor = WizardStepAction.Action(stringResource(id = R.string.dfu_progress_run), enabled = false),
        state = WizardStepState.INACTIVE,
        showVerticalDivider = false,
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFUCompletedProgressView(
    viewEntity: ProgressItemViewEntity = ProgressItemViewEntity()
) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_progress),
        state = WizardStepState.COMPLETED,
        showVerticalDivider = false,
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFURunningProgressView(
    viewEntity: ProgressItemViewEntity = ProgressItemViewEntity(),
    onEvent: (DFUViewEvent) -> Unit
) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_progress),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_abort),
            onClick = { onEvent(OnAbortButtonClick) },
            dangerous = true
        ),
        state = WizardStepState.CURRENT,
        showVerticalDivider = false,
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFUIdleProgressView(
    onEvent: (DFUViewEvent) -> Unit
) {
    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_progress),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_progress_run),
            onClick = { onEvent(OnInstallButtonClick) }
        ),
        state = WizardStepState.CURRENT,
        showVerticalDivider = false,
    ) {
        ProgressItem(ProgressItemViewEntity())
    }
}

@Composable
private fun ProgressItem(viewEntity: ProgressItemViewEntity) {
    Column(
        modifier = Modifier.padding(start = 8.dp)
    ) {
        ProgressItem(
            text = BootloaderItem.toDisplayString(status = viewEntity.bootloaderStatus),
            status = viewEntity.bootloaderStatus,
            iconRightPadding = 24.dp,
        )
        Spacer(modifier = Modifier.size(8.dp))
        ProgressItem(
            text = DfuItem.toDisplayString(status = viewEntity.dfuStatus),
            status = viewEntity.dfuStatus,
            iconRightPadding = 24.dp,
        )
        Spacer(modifier = Modifier.size(8.dp))

        if (viewEntity.installationStatus == ProgressItemStatus.WORKING) {
            Column {
                ProgressItem(
                    text = viewEntity.progress.toLabel(),
                    status = viewEntity.installationStatus,
                    iconRightPadding = 24.dp,
                ) {
                    LinearProgressIndicator(
                        progress = viewEntity.progress.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            id = R.string.dfu_display_status_progress_speed,
                            viewEntity.progress.avgSpeed
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                }
            }
        } else {
            ProgressItem(
                text = FirmwareItem.toDisplayString(status = viewEntity.installationStatus),
                status = viewEntity.installationStatus,
                iconRightPadding = 24.dp,
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        if (viewEntity.resultStatus != ProgressItemStatus.ERROR) {
            ProgressItem(
                text = stringResource(id = R.string.dfu_progress_stage_completed),
                status = viewEntity.resultStatus,
                iconRightPadding = 24.dp,
            )
        } else {
            ProgressItem(
                text = stringResource(
                    id = R.string.dfu_progress_stage_error,
                    viewEntity.errorMessage ?: stringResource(id = R.string.dfu_unknown)
                ),
                status = viewEntity.resultStatus,
                iconRightPadding = 24.dp,
            )
        }
    }
}

@Composable
private fun Uploading.toLabel(): String {
    return if (partsTotal > 1) {
        stringResource(id = R.string.dfu_display_status_progress_update_parts, currentPart, partsTotal, progress)
    } else {
        stringResource(id = R.string.dfu_display_status_progress_update, progress)
    }
}
