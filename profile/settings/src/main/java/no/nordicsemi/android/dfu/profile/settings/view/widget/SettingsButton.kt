package no.nordicsemi.android.dfu.profile.settings.view.widget

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun SettingsButton(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val color = if (enabled) {
        LocalContentColor.current
    } else {
        LocalContentColor.current.copy(alpha = 0.38f)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = color,
        )

        description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = color,
            )
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
    name = "Enabled",
    group = "SettingsButton",
    heightDp = 100
)
@Composable
private fun SettingsButtonPreview_Enabled() {
    SettingsButton(
        text = "Button",
        description = "Description",
        onClick = {},
    )
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true,
    name = "Disabled",
    group = "SettingsButton",
    heightDp = 100
)
@Composable
private fun SettingsButtonPreview_Disabled() {
    SettingsButton(
        text = "Button",
        description = "Description",
        onClick = {},
        enabled = false,
    )
}