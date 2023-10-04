package no.nordicsemi.android.dfu.profile.settings.view.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import no.nordicsemi.android.common.theme.NordicTheme
import no.nordicsemi.android.dfu.profile.settings.R

@Composable
internal fun SettingsTimeSlider(
    text: String,
    modifier: Modifier = Modifier,
    description: String?,
    value: Int,
    valueRange: IntRange,
    stepInMilliseconds: Int,
    onChange: (Int) -> Unit
) {
    SettingsSlider(
        text = text,
        modifier = modifier,
        description = description,
        value = value,
        valueRange = valueRange,
        unit = stringResource(id = R.string.dfu_settings_time),
        step = stepInMilliseconds,
        onChange = onChange,
    )
}

@Preview(heightDp = 120)
@Composable
private fun SettingsSliderPreview() {
    NordicTheme {
        SettingsTimeSlider(
            text = "Slider",
            description = "Description",
            value = 1000,
            valueRange = 0..20_000,
            stepInMilliseconds = 1_000,
            onChange = {},
        )
    }
}