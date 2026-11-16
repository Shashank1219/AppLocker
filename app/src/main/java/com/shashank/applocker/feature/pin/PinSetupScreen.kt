package com.shashank.applocker.feature.pin

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.components.SecureWindow

@Composable
fun PinSetupScreen(
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinSetupViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { if (it == PinSetupEffect.Completed) onCompleted() }
    }
    val confirming = state.step == SetupStep.CONFIRM
    PinEntryContent(
        title = stringResource(if (confirming) R.string.pin_setup_title_confirm else R.string.pin_setup_title_enter),
        subtitle = stringResource(
            if (confirming) R.string.pin_setup_subtitle_confirm else R.string.pin_setup_subtitle_enter,
        ),
        pin = state.pin,
        showPin = state.showPin,
        errorText = state.error?.let { errorMessage(it) },
        enabled = !state.isSaving,
        onDigit = { viewModel.onEvent(PinSetupEvent.DigitEntered(it)) },
        onBackspace = { viewModel.onEvent(PinSetupEvent.Backspace) },
        onToggleShow = { viewModel.onEvent(PinSetupEvent.ToggleShowPin) },
        modifier = modifier,
        footer = {
            if (state.error == PinSetupError.StorageFailure) {
                Button(onClick = { viewModel.onEvent(PinSetupEvent.RetrySave) }) {
                    Text(stringResource(R.string.pin_retry))
                }
            } else if (!confirming) {
                Text(
                    stringResource(R.string.pin_recovery_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun errorMessage(error: PinSetupError): String = stringResource(
    when (error) {
        is PinSetupError.Rejected -> when (error.reason) {
            PinRejection.NOT_SIX_DIGITS -> R.string.pin_error_not_six
            PinRejection.REPEATED_DIGITS -> R.string.pin_error_repeated
            PinRejection.SEQUENTIAL -> R.string.pin_error_sequential
        }
        PinSetupError.Mismatch -> R.string.pin_error_mismatch
        PinSetupError.StorageFailure -> R.string.pin_error_storage
    },
)
