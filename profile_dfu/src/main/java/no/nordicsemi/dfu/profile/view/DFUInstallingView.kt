package no.nordicsemi.dfu.profile.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.material.you.ScreenSection
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.viewmodel.DFUViewModel

@Composable
internal fun DFUInstallingView(status: String) {
    val viewModel: DFUViewModel = hiltViewModel()

    Box(modifier = Modifier.fillMaxSize()) {
        ScreenSection {
            CircularProgressIndicator()

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = status)
        }

        FloatingActionButton(onClick = { viewModel.onEvent(OnAbortButtonClick) }) {
            Icon(Icons.Default.Close, contentDescription = stringResource(id = R.string.dfu_ic_abort))
        }
    }
}
