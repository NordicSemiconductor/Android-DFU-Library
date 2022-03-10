package no.nordicsemi.dfu.profile.main.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.main.data.ProgressUpdate
import no.nordicsemi.ui.scanner.ui.exhaustive

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
    }.exhaustive
}

@Composable
private fun DisabledDFUProgressView(viewEntity: ProgressItemViewEntity = ProgressItemViewEntity()) {
    DisabledCardComponent(
        titleIcon = R.drawable.ic_file_upload,
        title = stringResource(id = R.string.dfu_progress),
        description = stringResource(id = R.string.dfu_progress_idle),
        primaryButtonTitle = stringResource(id = R.string.dfu_progress_run),
        showVerticalDivider = false
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFUCompletedProgressView(
    viewEntity: ProgressItemViewEntity = ProgressItemViewEntity()
) {
    CardComponent(
        titleIcon = R.drawable.ic_file_upload,
        title = stringResource(id = R.string.dfu_progress),
        description = stringResource(id = R.string.dfu_progress_running),
        showVerticalDivider = false
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFURunningProgressView(
    viewEntity: ProgressItemViewEntity = ProgressItemViewEntity(),
    onEvent: (DFUViewEvent) -> Unit
) {
    CardComponent(
        titleIcon = R.drawable.ic_file_upload,
        title = stringResource(id = R.string.dfu_progress),
        description = stringResource(id = R.string.dfu_progress_running),
        primaryButtonTitle = stringResource(id = R.string.dfu_abort),
        primaryButtonAction = { onEvent(OnAbortButtonClick) },
        redButtonColor = true,
        showVerticalDivider = false
    ) {
        ProgressItem(viewEntity)
    }
}

@Composable
private fun DFUIdleProgressView(
    onEvent: (DFUViewEvent) -> Unit
) {
    CardComponent(
        titleIcon = R.drawable.ic_file_upload,
        title = stringResource(id = R.string.dfu_progress),
        description = stringResource(id = R.string.dfu_progress_running),
        primaryButtonTitle = stringResource(id = R.string.dfu_progress_run),
        primaryButtonAction = { onEvent(OnInstallButtonClick) },
        showVerticalDivider = false
    ) {
        ProgressItem(ProgressItemViewEntity())
    }
}

@Composable
private fun ProgressItem(viewEntity: ProgressItemViewEntity) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        ProgressItem(
            BootloaderItem.toDisplayString(status = viewEntity.bootloaderStatus),
            viewEntity.bootloaderStatus
        )
        Spacer(modifier = Modifier.size(8.dp))
        ProgressItem(DfuItem.toDisplayString(status = viewEntity.dfuStatus), viewEntity.dfuStatus)
        Spacer(modifier = Modifier.size(8.dp))

        if (viewEntity.installationStatus == ProgressItemStatus.WORKING) {
            Column {
                ProgressItem(
                    viewEntity.progress.toLabel(),
                    viewEntity.installationStatus
                )
                LinearProgressIndicator(
                    progress = viewEntity.progress.progress/100f,
                    modifier = Modifier
                        .padding(horizontal = 32.dp + 16.dp)
                        .fillMaxWidth()
                )
                Text(
                    text = stringResource(id = R.string.dfu_display_status_progress_speed, viewEntity.progress.avgSpeed),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp + 16.dp),
                    textAlign = TextAlign.End
                )
            }
        } else {
            ProgressItem(
                FirmwareItem.toDisplayString(status = viewEntity.installationStatus),
                viewEntity.installationStatus
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        if (viewEntity.resultStatus != ProgressItemStatus.ERROR) {
            ProgressItem(
                stringResource(id = R.string.dfu_progress_stage_completed),
                viewEntity.resultStatus
            )
        } else {
            ProgressItem(
                stringResource(
                    id = R.string.dfu_progress_stage_error,
                    viewEntity.errorMessage ?: stringResource(id = R.string.dfu_unknown)
                ),
                viewEntity.resultStatus
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun ProgressItem(text: String, status: ProgressItemStatus) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(status.toImageRes()),
            contentDescription = stringResource(id = R.string.dfu_progress_icon),
            tint = status.toIconColor()
        )
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = status.toTextColor(),
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Composable
private fun ProgressItemStatus.toIconColor(): Color {
    return when (this) {
        ProgressItemStatus.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        ProgressItemStatus.WORKING -> MaterialTheme.colorScheme.onBackground
        ProgressItemStatus.SUCCESS -> colorResource(id = R.color.nordicGrass)
        ProgressItemStatus.ERROR -> MaterialTheme.colorScheme.error
    }
}

@Composable
private fun ProgressItemStatus.toTextColor(): Color {
    return when (this) {
        ProgressItemStatus.DISABLED -> MaterialTheme.colorScheme.surfaceVariant
        ProgressItemStatus.WORKING,
        ProgressItemStatus.SUCCESS,
        ProgressItemStatus.ERROR -> MaterialTheme.colorScheme.onBackground
    }
}

@DrawableRes
private fun ProgressItemStatus.toImageRes(): Int {
    return when (this) {
        ProgressItemStatus.DISABLED -> R.drawable.ic_dot
        ProgressItemStatus.WORKING -> R.drawable.ic_arrow_right
        ProgressItemStatus.SUCCESS -> R.drawable.ic_check
        ProgressItemStatus.ERROR -> R.drawable.ic_cross
    }
}

@Composable
private fun ProgressUpdate.toLabel(): String {
    return if (partsTotal > 1) {
        stringResource(id = R.string.dfu_display_status_progress_update_parts, currentPart, partsTotal, progress)
    } else {
        stringResource(id = R.string.dfu_display_status_progress_update, progress)
    }
}
