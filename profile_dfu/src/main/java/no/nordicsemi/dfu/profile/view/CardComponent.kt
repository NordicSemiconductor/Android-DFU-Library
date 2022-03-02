package no.nordicsemi.dfu.profile.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import no.nordicsemi.android.material.you.VerticalDivider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardComponent(
    @DrawableRes titleIcon: Int,
    title: String,
    description: String,
    primaryButtonTitle: String? = null,
    primaryButtonAction: (() -> Unit)? = null,
    secondaryButtonTitle: String? = null,
    secondaryButtonAction: (() -> Unit)? = null,
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
        }

        Row {
            VerticalDivider()

            Spacer(modifier = Modifier.size(16.dp))

            Column {
                content()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    secondaryButtonTitle?.let {
                        OutlinedButton(onClick = { secondaryButtonAction?.invoke() }) {
                            Text(text = it)
                        }
                    }

                    primaryButtonTitle?.let {
                        Button(onClick = { primaryButtonAction?.invoke() }) {
                            Text(text = it)
                        }
                    }
                }
            }
        }
    }
}
