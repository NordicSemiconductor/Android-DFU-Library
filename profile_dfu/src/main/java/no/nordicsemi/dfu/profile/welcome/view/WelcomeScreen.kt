package no.nordicsemi.dfu.profile.welcome.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.dfu.profile.R
import no.nordicsemi.dfu.profile.welcome.viewmodel.WelcomeViewModel

@Composable
fun WelcomeScreen() {
    val viewModel = hiltViewModel<WelcomeViewModel>()
    val firstRun = viewModel.firstRun.collectAsState().value

    Box {
        Column {

            if (firstRun) {
                WelcomeAppBar()
            } else {
                WelcomeAppBar { viewModel.navigateUp() }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                Image(
                    painter = painterResource(id = R.drawable.dfu),
                    contentDescription = stringResource(id = R.string.dfu_explanation_image),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(16.dp)
                )

                Spacer(modifier = Modifier.size(32.dp))

                Text(
                    text = stringResource(id = R.string.dfu_about_text),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.size(76.dp))
            }
        }


        ExtendedFloatingActionButton(
            text = { Text(text = stringResource(id = R.string.dfu_start)) },
            icon = { Icon(painter = painterResource(id = R.drawable.ic_start), contentDescription = stringResource(id = R.string.dfu_start)) },
            onClick = { viewModel.navigateUp() },
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun WelcomeAppBar() {
    SmallTopAppBar(
        title = { Text(stringResource(id = R.string.dfu_welcome)) },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = colorResource(id = R.color.appBarColor),
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        )
    )
}

@Composable
private fun WelcomeAppBar(onNavigateUpClick: () -> Unit) {
    SmallTopAppBar(
        title = { Text(stringResource(id = R.string.dfu_welcome)) },
        colors = TopAppBarDefaults.smallTopAppBarColors(
            scrolledContainerColor = MaterialTheme.colorScheme.primary,
            containerColor = colorResource(id = R.color.appBarColor),
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        navigationIcon = {
            IconButton(onClick = { onNavigateUpClick() }) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(id = R.string.dfu_navigate_up)
                )
            }
        }
    )
}
