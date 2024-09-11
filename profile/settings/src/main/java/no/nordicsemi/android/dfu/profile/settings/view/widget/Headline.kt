package no.nordicsemi.android.dfu.profile.settings.view.widget

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
internal fun Headline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.secondary
    )
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true, widthDp = 200, heightDp = 50)
@Composable
private fun HeadlinePreview() {
    Headline(text = "Headline")
}