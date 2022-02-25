package no.nordicsemi.dfu.profile.view

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import no.nordicsemi.android.material.you.ScreenSection

@Composable
internal fun DFUInstallingView(status: String) {
    ScreenSection {
        CircularProgressIndicator()

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = status)
    }
}
