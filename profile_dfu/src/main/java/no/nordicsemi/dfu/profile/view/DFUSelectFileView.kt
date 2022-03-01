package no.nordicsemi.dfu.profile.view

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.data.ZipFile
import no.nordicsemi.ui.scanner.ui.exhaustive

internal sealed class DFUSelectFileViewEntity

internal data class NotSelectedFileViewEntity(val isError: Boolean = false) : DFUSelectFileViewEntity()

internal data class SelectedFileViewEntity(val zipFile: ZipFile) : DFUSelectFileViewEntity()

@Composable
internal fun DFUSelectFileView(viewEntity: DFUSelectFileViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    when (viewEntity) {
        is NotSelectedFileViewEntity -> DFUNotSelectedFileView(viewEntity, onEvent)
        is SelectedFileViewEntity -> DFUSelectFileView(viewEntity.zipFile)
    }.exhaustive
}

@Composable
internal fun DFUNotSelectedFileView(viewEntity: NotSelectedFileViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { onEvent(OnZipFileSelected(it)) }
    }

    CardComponent(
        titleIcon = R.drawable.ic_upload_file,
        title = stringResource(id = R.string.dfu_choose_file),
        description = stringResource(id = R.string.dfu_choose_not_selected),
        primaryButtonTitle = stringResource(id = R.string.dfu_select_file),
        primaryButtonAction = { launcher.launch(DfuBaseService.MIME_TYPE_ZIP) }
    ) {
        Text(
            text = stringResource(id = R.string.dfu_choose_info),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (viewEntity.isError) {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(id = R.string.dfu_load_file_error),
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DFUSelectFileView(zipFile: ZipFile) {
    CardComponent(
        titleIcon = R.drawable.ic_upload_file,
        title = stringResource(id = R.string.dfu_choose_file),
        description = stringResource(id = R.string.dfu_choose_selected),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(id = R.string.dfu_file_name, zipFile.name),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = stringResource(id = R.string.dfu_file_size, zipFile.size),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
