/*
 * Copyright (c) 2022, Nordic Semiconductor
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list
 * of conditions and the following disclaimer in the documentation and/or other materials
 * provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be
 * used to endorse or promote products derived from this software without specific prior
 * written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
 * OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package no.nordicsemi.android.dfu.profile.welcome.view

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import no.nordicsemi.android.common.theme.view.NordicAppBar
import no.nordicsemi.android.dfu.profile.R
import no.nordicsemi.android.dfu.profile.welcome.viewmodel.WelcomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WelcomeScreen() {
    val viewModel = hiltViewModel<WelcomeViewModel>()
    val firstRun = viewModel.firstRun.collectAsState().value

    Box {
        Column {
            if (firstRun) {
                NordicAppBar(
                    text = stringResource(R.string.dfu_welcome),
                )
            } else {
                NordicAppBar(
                    text = stringResource(R.string.dfu_about_app),
                    onNavigationButtonClick = { viewModel.navigateUp() },
                )
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

        if (firstRun) {
            ElevatedButton(
                onClick = { viewModel.navigateUp() },
                colors = ButtonDefaults.buttonColors(),
                modifier = Modifier
                    .padding(16.dp)
                    .width(150.dp)
                    .align(Alignment.BottomCenter),
            ) {
                Text(text = stringResource(id = R.string.dfu_start))
            }
        }
    }
}
