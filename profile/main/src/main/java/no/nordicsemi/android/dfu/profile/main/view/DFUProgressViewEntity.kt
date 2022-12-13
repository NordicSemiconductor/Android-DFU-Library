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

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import no.nordicsemi.android.common.theme.view.ProgressItemStatus
import no.nordicsemi.android.dfu.profile.main.R
import no.nordicsemi.android.dfu.profile.main.data.Uploading

internal sealed class DFUProgressViewEntity {

    companion object {

        fun createBootloaderStage() = WorkingProgressViewEntity(
            ProgressItemViewEntity(bootloaderStatus = ProgressItemStatus.WORKING)
        )

        fun createDfuStage() = WorkingProgressViewEntity(
            ProgressItemViewEntity(
                bootloaderStatus = ProgressItemStatus.SUCCESS,
                dfuStatus = ProgressItemStatus.WORKING,
            )
        )

        fun createInstallingStage(progress: Uploading) = WorkingProgressViewEntity(
            ProgressItemViewEntity(
                bootloaderStatus = ProgressItemStatus.SUCCESS,
                dfuStatus = ProgressItemStatus.SUCCESS,
                installationStatus = ProgressItemStatus.WORKING,
                progress = progress
            )
        )

        fun createSuccessStage() = WorkingProgressViewEntity(
            ProgressItemViewEntity(
                bootloaderStatus = ProgressItemStatus.SUCCESS,
                dfuStatus = ProgressItemStatus.SUCCESS,
                installationStatus = ProgressItemStatus.SUCCESS,
                resultStatus = ProgressItemStatus.SUCCESS
            )
        )

        fun ProgressItemViewEntity.createErrorStage(errorMessage: String?) = WorkingProgressViewEntity(
            ProgressItemViewEntity(
                bootloaderStatus = bootloaderStatus.createErrorStatus(),
                dfuStatus = dfuStatus.createErrorStatus(),
                installationStatus = installationStatus.createErrorStatus(),
                resultStatus = ProgressItemStatus.ERROR,
                errorMessage = errorMessage
            )
        )

        private fun ProgressItemStatus.createErrorStatus() = when (this) {
            ProgressItemStatus.SUCCESS -> ProgressItemStatus.SUCCESS
            else -> ProgressItemStatus.ERROR
        }
    }
}

internal object DisabledProgressViewEntity : DFUProgressViewEntity()

internal data class WorkingProgressViewEntity(
    val status: ProgressItemViewEntity = ProgressItemViewEntity()
) : DFUProgressViewEntity()

internal data class ProgressItemViewEntity(
    val bootloaderStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val dfuStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val installationStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val resultStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val progress: Uploading = Uploading(),
    val errorMessage: String? = null
) {
    fun isRunning() = !isCompleted() && (
       bootloaderStatus != ProgressItemStatus.DISABLED ||
       dfuStatus != ProgressItemStatus.DISABLED ||
       installationStatus == ProgressItemStatus.WORKING
    )

    fun isCompleted() =
       resultStatus == ProgressItemStatus.SUCCESS || resultStatus == ProgressItemStatus.ERROR
}

data class ProgressItemLabel(
    @StringRes val idleText: Int,
    @StringRes val workingText: Int,
    @StringRes val successText: Int,
) {
    @Composable
    fun toDisplayString(status: ProgressItemStatus) = when (status) {
        ProgressItemStatus.WORKING -> stringResource(id = workingText)
        ProgressItemStatus.SUCCESS -> stringResource(id = successText)
        ProgressItemStatus.DISABLED,
        ProgressItemStatus.ERROR -> stringResource(id = idleText)
    }
}

val BootloaderItem = ProgressItemLabel(
    R.string.dfu_bootloader_idle,
    R.string.dfu_bootloader_working,
    R.string.dfu_bootloader_success
)

val DfuItem = ProgressItemLabel(
    R.string.dfu_process_idle,
    R.string.dfu_process_working,
    R.string.dfu_process_success
)

val FirmwareItem = ProgressItemLabel(
    R.string.dfu_firmware_idle,
    R.string.dfu_firmware_working,
    R.string.dfu_firmware_success
)
