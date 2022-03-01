package no.nordicsemi.dfu.profile.view

internal sealed class DFUProgressViewEntity {

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

        fun createInstallingStage(progress: Int): WorkingProgressViewEntity {
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

internal object DisabledProgressViewEntity : DFUProgressViewEntity()

internal data class WorkingProgressViewEntity(
    val status: ProgressItemViewEntity = ProgressItemViewEntity()
) : DFUProgressViewEntity()

data class ProgressItemViewEntity(
    val bootloaderStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val dfuStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val installationStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val resultStatus: ProgressItemStatus = ProgressItemStatus.DISABLED,
    val progress: Int = 0,
    val errorMessage: String? = null
) {

    fun isRunning(): Boolean {
        return (bootloaderStatus != ProgressItemStatus.DISABLED
                || dfuStatus != ProgressItemStatus.DISABLED
                || installationStatus != ProgressItemStatus.DISABLED)
                && !isCompleted()

    }

    fun isCompleted(): Boolean {
        return resultStatus == ProgressItemStatus.SUCCESS || resultStatus == ProgressItemStatus.ERROR
    }
}

enum class ProgressItemStatus {
    DISABLED, WORKING, SUCCESS, ERROR
}
