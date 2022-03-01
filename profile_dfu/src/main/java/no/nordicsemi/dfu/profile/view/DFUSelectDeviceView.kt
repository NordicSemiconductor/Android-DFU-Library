package no.nordicsemi.dfu.profile.view

import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.ui.scanner.DiscoveredBluetoothDevice
import no.nordicsemi.ui.scanner.ui.exhaustive

internal sealed class DFUSelectDeviceViewEntity

internal object DisabledSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

internal object NotSelectedDeviceViewEntity : DFUSelectDeviceViewEntity()

internal data class SelectedDeviceViewEntity(val device: DiscoveredBluetoothDevice) : DFUSelectDeviceViewEntity()

@Composable
internal fun DFUSelectDeviceView(viewEntity: DFUSelectDeviceViewEntity, onEvent: (DFUViewEvent) -> Unit) {
    when (viewEntity) {
        DisabledSelectedDeviceViewEntity -> DFUDisabledSelectedDeviceView()
        is NotSelectedDeviceViewEntity -> DFUNotSelectedDeviceView(onEvent)
        is SelectedDeviceViewEntity -> DFUSelectDeviceView(viewEntity)
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
            modifier = Modifier.padding(horizontal = 16.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DFUSelectDeviceView(viewEntity: SelectedDeviceViewEntity) {
    CardComponent(
        titleIcon = R.drawable.ic_bluetooth,
        title = stringResource(id = R.string.dfu_device),
        description = stringResource(id = R.string.dfu_choose_selected),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = stringResource(id = R.string.dfu_device_name, viewEntity.device.displayName() ?: "No name"),
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.size(4.dp))

            Text(
                text = stringResource(id = R.string.dfu_device_address, viewEntity.device.displayAddress()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
