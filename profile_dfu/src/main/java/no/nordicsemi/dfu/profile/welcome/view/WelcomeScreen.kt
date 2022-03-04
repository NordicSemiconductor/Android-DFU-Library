package no.nordicsemi.dfu.profile.welcome.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.welcome.viewmodel.WelcomeViewModel

@Composable
fun WelcomeScreen() {
    val viewModel = hiltViewModel<WelcomeViewModel>()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(16.dp)
    ) {

        Spacer(modifier = Modifier.size(16.dp))

        Text(
            text = stringResource(id = R.string.dfu_welcome),
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(modifier = Modifier.size(32.dp))

        Image(
            painter = painterResource(id = R.drawable.dfu),
            contentDescription = stringResource(id = R.string.dfu_explanation_image),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.size(32.dp))

        Text(
            text = stringResource(id = R.string.dfu_about_text),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(modifier = Modifier.size(16.dp))

        Button(onClick = { viewModel.navigateUp() }) {
            Text(text = stringResource(id = R.string.dfu_start))
        }

        Spacer(modifier = Modifier.size(16.dp))
    }
}
