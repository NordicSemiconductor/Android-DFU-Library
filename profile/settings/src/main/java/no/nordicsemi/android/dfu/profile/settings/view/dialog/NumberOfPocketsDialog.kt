package no.nordicsemi.android.dfu.profile.settings.view.dialog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import no.nordicsemi.android.dfu.profile.settings.R

@Composable
internal fun NumberOfPocketsDialog(
    numberOfPockets: Int,
    onDismiss: () -> Unit,
    onNumberOfPocketsChange: (Int) -> Unit
) {
    var numberOfPocketsState by rememberSaveable { mutableStateOf("$numberOfPockets") }
    var showError by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.dfu_settings_number_of_pockets))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = numberOfPocketsState,
                    onValueChange = { newValue ->
                        val value = newValue.toIntOrNull()
                        if (value != null) {
                            numberOfPocketsState = "$value"
                            showError = false
                        } else {
                            numberOfPocketsState = ""
                            showError = true
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    label = { Text(text = stringResource(id = R.string.dfu_settings_number_of_pockets)) },
                )
                if (showError) {
                    Text(text = stringResource(id = R.string.dfu_parse_int_error))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onNumberOfPocketsChange(numberOfPocketsState.toInt())
                },
                enabled = !showError
            ) {
                Text(text = stringResource(id = R.string.dfu_macro_dialog_confirm))
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.dfu_macro_dialog_dismiss))
            }
        }
    )
}