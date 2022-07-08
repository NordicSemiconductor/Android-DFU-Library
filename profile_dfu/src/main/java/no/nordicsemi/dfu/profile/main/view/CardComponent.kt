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

package no.nordicsemi.dfu.profile.main.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
internal fun CardComponent(
    @DrawableRes titleIcon: Int,
    title: String,
    description: String,
    primaryButtonTitle: String? = null,
    primaryButtonAction: (() -> Unit)? = null,
    primaryButtonEnabled: Boolean = true,
    redButtonColor: Boolean = false,
    secondaryButtonTitle: String? = null,
    secondaryButtonAction: (() -> Unit)? = null,
    secondaryButtonEnabled: Boolean = true,
    showVerticalDivider: Boolean = true,
    isRunning: Boolean = false,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(id = titleIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp).padding(8.dp))
                } else {
                    secondaryButtonTitle?.let {
                        OutlinedButton(
                            onClick = { secondaryButtonAction?.invoke() },
                            enabled = secondaryButtonEnabled
                        ) {
                            Text(text = it)
                        }
                    }

                    primaryButtonTitle?.let {
                        val color = if (redButtonColor) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                        Button(
                            onClick = { primaryButtonAction?.invoke() },
                            colors = color,
                            enabled = primaryButtonEnabled
                        ) {
                            Text(text = it)
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (showVerticalDivider) {
                Box(
                    modifier = Modifier
                        .padding(start = 34.dp)
                        .fillMaxHeight()
                        .width(4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) { }

                Spacer(modifier = Modifier.size(16.dp))
            } else {
                Spacer(modifier = Modifier.size(8.dp))
            }

            content()
        }
    }
}
