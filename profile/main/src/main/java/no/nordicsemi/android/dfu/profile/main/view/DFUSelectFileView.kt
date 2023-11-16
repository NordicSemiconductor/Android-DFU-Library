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

import android.content.ActivityNotFoundException
import android.os.Parcelable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import no.nordicsemi.android.common.core.parseBold
import no.nordicsemi.android.common.theme.view.WizardStepAction
import no.nordicsemi.android.common.theme.view.WizardStepComponent
import no.nordicsemi.android.common.theme.view.WizardStepState
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.profile.main.R
import no.nordicsemi.android.dfu.profile.main.data.ZipFile

internal sealed class DFUSelectFileViewEntity : Parcelable

@Parcelize
internal data class NotSelectedFileViewEntity(
    val isError: Boolean = false,
    val isRunning: Boolean = false
) : DFUSelectFileViewEntity()

@Parcelize
internal data class SelectedFileViewEntity(val zipFile: ZipFile) : DFUSelectFileViewEntity()

private val icon = Icons.Outlined.FolderZip

@Composable
internal fun DFUSelectFileView(
    viewEntity: DFUSelectFileViewEntity,
    enabled: Boolean,
    onEvent: (DFUViewEvent) -> Unit
) {
    when (viewEntity) {
        is NotSelectedFileViewEntity -> DFUNotSelectedFileView(viewEntity, onEvent)
        is SelectedFileViewEntity -> DFUSelectFileView(viewEntity.zipFile, onEvent, enabled)
    }
}

@Composable
internal fun DFUNotSelectedFileView(viewEntity: NotSelectedFileViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onEvent(OnZipFileSelected(it)) }
    }

    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_choose_file),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_select_file),
            onClick = {
                try {
                    launcher.launch(DfuBaseService.MIME_TYPE_ZIP)
                } catch (e: ActivityNotFoundException) {
                    try {
                        launcher.launch("*/*")
                    } catch (e1: ActivityNotFoundException) {
                        // Handle
                    }
                }
            }
        ),
        state = WizardStepState.CURRENT,
    ) {
        if (viewEntity.isError) {
            Text(
                text = stringResource(id = R.string.dfu_load_file_error),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        } else {
            Text(
                text = stringResource(id = R.string.dfu_choose_info),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val FILE_NAME = "Name: <b>%s</b>"
private const val FILE_SIZE = "Size: <b>%d</b> bytes"

@Composable
internal fun DFUSelectFileView(
    zipFile: ZipFile,
    onEvent: (DFUViewEvent) -> Unit,
    enabled: Boolean,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onEvent(OnZipFileSelected(it)) }
    }

    WizardStepComponent(
        icon = icon,
        title = stringResource(id = R.string.dfu_choose_file),
        decor = WizardStepAction.Action(
            text = stringResource(id = R.string.dfu_select_file),
            onClick = {
                try {
                    launcher.launch(DfuBaseService.MIME_TYPE_ZIP)
                } catch (e: ActivityNotFoundException) {
                    try {
                        launcher.launch("*/*")
                    } catch (e1: ActivityNotFoundException) {
                        // Handle
                    }
                }
            },
            enabled = enabled,
        ),
        state = WizardStepState.COMPLETED,
    ) {
        Text(
            text = String.format(FILE_NAME, zipFile.name).parseBold(),
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            text = String.format(FILE_SIZE, zipFile.size).parseBold(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
