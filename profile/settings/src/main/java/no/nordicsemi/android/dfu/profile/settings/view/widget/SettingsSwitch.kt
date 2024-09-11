package no.nordicsemi.android.dfu.profile.settings.view.widget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun SettingsSwitch(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
            )

            description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Checkbox(checked = isChecked, onCheckedChange = { onClick() })
    }
}

@Preview(heightDp = 100)
@Composable
private fun SettingsSwitchPreview() {
    SettingsSwitch(
        text = "Switch",
        description = "Description",
        isChecked = true,
        onClick = {},
    )
}