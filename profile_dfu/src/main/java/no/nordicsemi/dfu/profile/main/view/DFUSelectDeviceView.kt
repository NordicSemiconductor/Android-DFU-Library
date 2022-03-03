package no.nordicsemi.dfu.profile.main.view

import android.os.Parcelable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.parcelize.Parcelize
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.main.util.parseBold
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import no.nordicsemi.ui.scanner.ui.exhaustive

internal sealed class DFUSelectDeviceViewEntity : Parcelable

@Parcelize
internal object DisabledSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

@Parcelize
internal object NotSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

@Parcelize
internal data class SelectedDeviceViewEntity(val device: DiscoveredBluetoothDevice) : DFUSelectDeviceViewEntity()

@Composable
internal fun DFUSelectedDeviceView(isRunning: Boolean, viewEntity: DFUSelectDeviceViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    when (viewEntity) {
        DisabledSelectedDeviceViewEntity -> DFUDisabledSelectedDeviceView()
        is NotSelectedDeviceViewEntity -> DFUNotSelectedDeviceView(onEvent)
        is SelectedDeviceViewEntity -> if (!isRunning) {
            DFUSelectedDeviceView(viewEntity, onEvent)
        } else {
            DFUSelectedDeviceNoActionView(viewEntity)
        }
    }.exhaustive
}

@Composable
internal fun DFUDisabledSelectedDeviceView() {
    DisabledCardComponent(
        titleIcon = R.drawable.ic_bluetooth,
        title = stringResource(id = R.string.dfu_device),
        description = stringResource(id = R.string.dfu_choose_not_selected),
        primaryButtonTitle = stringResource(id = R.string.dfu_select_device),
    ) {
        Text(
            text = stringResource(id = R.string.dfu_select_device_info),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
internal fun DFUNotSelectedDeviceView(onEvent: (DFUViewEvent) -> Unit) {
    CardComponent(
        titleIcon = R.drawable.ic_bluetooth,
        title = stringResource(id = R.string.dfu_device),
        description = stringResource(id = R.string.dfu_choose_not_selected),
        primaryButtonTitle = stringResource(id = R.string.dfu_select_device),
        primaryButtonAction = { onEvent(OnSelectDeviceButtonClick) }
    ) {
        Text(
            text = stringResource(id = R.string.dfu_select_device_info),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private const val DEVICE_NAME = "Name: <b>%s</b>"
private const val DEVICE_ADDRESS = "Address: <b>%s</b>"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DFUSelectedDeviceView(viewEntity: SelectedDeviceViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    CardComponent(
        titleIcon = R.drawable.ic_bluetooth,
        title = stringResource(id = R.string.dfu_device),
        description = stringResource(id = R.string.dfu_choose_selected),
        secondaryButtonTitle = stringResource(id = R.string.dfu_select_device),
        secondaryButtonAction = { onEvent(OnSelectDeviceButtonClick) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = String.format(DEVICE_NAME, viewEntity.device.displayName() ?: "No name").parseBold(),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = String.format(DEVICE_ADDRESS, viewEntity.device.displayAddress()).parseBold(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DFUSelectedDeviceNoActionView(viewEntity: SelectedDeviceViewEntity) {
    CardComponent(
        titleIcon = R.drawable.ic_bluetooth,
        title = stringResource(id = R.string.dfu_device),
        description = stringResource(id = R.string.dfu_choose_selected),
        secondaryButtonTitle = stringResource(id = R.string.dfu_select_device),
        secondaryButtonEnabled = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = String.format(DEVICE_NAME, viewEntity.device.displayName() ?: "No name").parseBold(),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = String.format(DEVICE_ADDRESS, viewEntity.device.displayAddress()).parseBold(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
