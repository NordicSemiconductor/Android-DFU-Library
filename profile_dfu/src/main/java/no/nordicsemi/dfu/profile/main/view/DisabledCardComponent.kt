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
internal fun DisabledCardComponent(
    @DrawableRes titleIcon: Int,
    title: String,
    description: String,
    primaryButtonTitle: String? = null,
    secondaryButtonTitle: String? = null,
    showVerticalDivider: Boolean = true,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(55.dp) //From ButtonDefaults = minHeight + 2*HorizontalPadding
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                secondaryButtonTitle?.let {
                    OutlinedButton(
                        onClick = { },
                        enabled = false,
                    ) {
                        Text(text = it)
                    }
                }

                primaryButtonTitle?.let {
                    Button(
                        onClick = { },
                        enabled = false,
                    ) {
                        Text(text = it)
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
