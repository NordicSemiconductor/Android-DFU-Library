package no.nordicsemi.dfu.view

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable

@Composable
fun FeatureButton(
    @DrawableRes iconId: Int,
    @StringRes nameCode: Int,
    @StringRes name: Int,
    isRunning: Boolean? = null,
    onClick: () -> Unit
) {

}
