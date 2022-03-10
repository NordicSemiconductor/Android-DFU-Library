package no.nordicsemi.dfu.profile.main.view

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import kotlinx.parcelize.Parcelize
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.main.data.ProgressUpdate

internal sealed class DFUProgressViewEntity : Parcelable {

    companion object {

        fun createBootloaderStage(): WorkingProgressViewEntity {
            return WorkingProgressViewEntity(ProgressItemViewEntity(bootloaderStatus = ProgressItemStatus.WORKING))
        }

        fun createDfuStage(): WorkingProgressViewEntity {
            return WorkingProgressViewEntity(
                ProgressItemViewEntity(
                    bootloaderStatus = ProgressItemStatus.SUCCESS,
                    dfuStatus = ProgressItemStatus.WORKING,
                )
            )
        }

        fun createInstallingStage(progress: ProgressUpdate): WorkingProgressViewEntity {
            return WorkingProgressViewEntity(
                ProgressItemViewEntity(
                    bootloaderStatus = ProgressItemStatus.SUCCESS,
                    dfuStatus = ProgressItemStatus.SUCCESS,
                    installationStatus = ProgressItemStatus.WORKING,
                    progress = progress
                )
            )
        }

        fun createSuccessStage(): WorkingProgressViewEntity {
            return WorkingProgressViewEntity(
                ProgressItemViewEntity(
                    bootloaderStatus = ProgressItemStatus.SUCCESS,
                    dfuStatus = ProgressItemStatus.SUCCESS,
                    installationStatus = ProgressItemStatus.SUCCESS,
                    resultStatus = ProgressItemStatus.SUCCESS
                )
            )
        }

        fun ProgressItemViewEntity.createErrorStage(errorMessage: String?): WorkingProgressViewEntity {
            return WorkingProgressViewEntity(
                ProgressItemViewEntity(
                    bootloaderStatus = bootloaderStatus.createErrorStatus(),
                    dfuStatus = dfuStatus.createErrorStatus(),
                    installationStatus = installationStatus.createErrorStatus(),
                    resultStatus = ProgressItemStatus.ERROR,
                    errorMessage = errorMessage
                )
            )
        }

        private fun ProgressItemStatus.createErrorStatus(): ProgressItemStatus {
            return if (this != ProgressItemStatus.SUCCESS) {
                ProgressItemStatus.ERROR
            } else {
                ProgressItemStatus.SUCCESS
            }
        }
    }
}

@Parcelize
internal object DisabledProgressViewEntity : DFUProgressViewEntity()

@Parcelize
internal data class WorkingProgressViewEntity(
    val status: ProgressItemViewEntity = ProgressItemViewEntity()
) : DFUProgressViewEntity()


@Parcelize
internal data class ProgressItemViewEntity(
    val bootloaderStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val dfuStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val installationStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val resultStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val progress: ProgressUpdate = ProgressUpdate(),
    val errorMessage: String? = null
) : Parcelable {

    fun isRunning(): Boolean {
        return (bootloaderStatus != ProgressItemStatus.DISABLED
                || dfuStatus != ProgressItemStatus.DISABLED
                || installationStatus == ProgressItemStatus.WORKING)
                && !isCompleted()

    }

    fun isCompleted(): Boolean {
        return resultStatus == ProgressItemStatus.SUCCESS || resultStatus == ProgressItemStatus.ERROR
    }
}

enum class ProgressItemStatus {
    DISABLED, WORKING, SUCCESS, ERROR
}

data class ProgressItemLabel(
    @StringRes
    val idleText: Int,
    @StringRes
    val workingText: Int,
    @StringRes
    val successText: Int,
) {

    @Composable
    fun toDisplayString(status: ProgressItemStatus): String {
        return when (status) {
            ProgressItemStatus.WORKING -> stringResource(id = workingText)
            ProgressItemStatus.SUCCESS -> stringResource(id = successText)
            ProgressItemStatus.DISABLED,
            ProgressItemStatus.ERROR -> stringResource(id = idleText)
        }
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
