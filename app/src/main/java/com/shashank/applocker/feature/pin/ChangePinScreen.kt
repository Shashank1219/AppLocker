package com.shashank.applocker.feature.pin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.domain.model.PinRejection
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.components.SecureWindow

@Composable
fun ChangePinScreen(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePinViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangePinContent(state = state, onEvent = viewModel::onEvent, onClose = onClose, modifier = modifier)
}

/** Stateless Change PIN UI. */
@Composable
fun ChangePinContent(
    state: ChangePinUiState,
    onEvent: (ChangePinEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.step == ChangePinStep.DONE) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.change_pin_done_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            Text(stringResource(R.string.change_pin_done_body), modifier = Modifier.padding(vertical = 12.dp))
            Button(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.change_pin_done_button)) }
        }
        return
    }
    val (title, subtitle) = when (state.step) {
        ChangePinStep.CURRENT -> R.string.change_pin_title_current to R.string.change_pin_subtitle_current
        ChangePinStep.NEW -> R.string.change_pin_title_new to R.string.change_pin_subtitle_new
        else -> R.string.change_pin_title_confirm to R.string.change_pin_subtitle_confirm
    }
    PinEntryContent(
        title = stringResource(title),
        subtitle = stringResource(subtitle),
        pin = state.pin,
        showPin = state.showPin,
        errorText = state.error?.let { stringResource(errorRes(it)) },
        enabled = !state.isBusy,
        onDigit = { onEvent(ChangePinEvent.Digit(it)) },
        onBackspace = { onEvent(ChangePinEvent.Backspace) },
        onToggleShow = { onEvent(ChangePinEvent.ToggleShowPin) },
        modifier = modifier,
        footer = {
            TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

private fun errorRes(error: ChangePinError): Int = when (error) {
    ChangePinError.IncorrectCurrent -> R.string.pin_error_incorrect
    ChangePinError.Locked -> R.string.settings_pin_locked
    ChangePinError.SameAsCurrent -> R.string.change_pin_error_same
    is ChangePinError.Rejected -> when (error.reason) {
        PinRejection.NOT_SIX_DIGITS -> R.string.pin_error_not_six
        PinRejection.REPEATED_DIGITS -> R.string.pin_error_repeated
        PinRejection.SEQUENTIAL -> R.string.pin_error_sequential
    }
    ChangePinError.Mismatch -> R.string.pin_error_mismatch
    ChangePinError.StorageFailure -> R.string.pin_error_storage
}
