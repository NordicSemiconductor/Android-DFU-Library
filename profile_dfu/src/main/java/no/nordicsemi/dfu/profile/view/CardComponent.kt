package no.nordicsemi.dfu.profile.view

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardComponent(
    @DrawableRes titleIcon: Int,
    title: String,
    description: String,
    primaryButtonTitle: String? = null,
    primaryButtonAction: (() -> Unit)? = null,
    redButtonColor: Boolean = false,
    secondaryButtonTitle: String? = null,
    secondaryButtonAction: (() -> Unit)? = null,
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

            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                secondaryButtonTitle?.let {
                    OutlinedButton(onClick = { secondaryButtonAction?.invoke() }) {
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
                        colors = color
                    ) {
                        Text(text = it)
                    }
                }

                //For measurement
                if (secondaryButtonTitle == null && primaryButtonTitle == null) {
                    Button(
                        onClick = {  },
                        modifier = Modifier.alpha(0f)
                    ) {
                        Text(text = "test")
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
