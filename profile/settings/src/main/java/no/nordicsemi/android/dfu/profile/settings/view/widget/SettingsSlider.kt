package no.nordicsemi.android.dfu.profile.settings.view.widget

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.common.ui.view.NordicSliderDefaults

@Composable
internal fun SettingsSlider(
    text: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: Int,
    valueRange: IntRange,
    unit: String? = null,
    step: Int = 1,
    onChange: (Int) -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
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

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            var currentValue by remember(value) { mutableIntStateOf(value) }
            Slider(
                value = currentValue.toFloat() / step,
                valueRange = valueRange.first.toFloat() / step..valueRange.last.toFloat() / step,
                onValueChange = { currentValue = (it + 0.1F).toInt() * step },
                onValueChangeFinished = { onChange(currentValue) },
                steps = (valueRange.last - valueRange.first) / step - 1,
                modifier = Modifier.weight(1f),
                colors = NordicSliderDefaults.colors()
            )
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.width(80.dp)
            ) {
                Text(
                    text = currentValue.toString(),
                    textAlign = TextAlign.End,
                )
                unit?.let {
                    Text(
                        modifier = Modifier.padding(start = 4.dp),
                        text = it,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Preview(heightDp = 120, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Composable
private fun SettingsSliderPreview() {
    SettingsSlider(
        text = "Slider",
        description = "Description",
        value = 15,
        valueRange = 0..20,
        onChange = {},
    )
}