package com.shashank.applocker.feature.pin

import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.components.SecureWindow
import com.shashank.applocker.ui.components.findActivity

@Composable
fun PinUnlockScreen(
    onUnlocked: () -> Unit,
    onRecoveryAuthorized: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PinUnlockViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            when (it) {
                PinUnlockEffect.Unlocked -> onUnlocked()
                PinUnlockEffect.RecoveryAuthorized -> onRecoveryAuthorized()
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(PinUnlockEvent.Refresh) }

    val activity = LocalContext.current.findActivity() as? FragmentActivity
    val credentialTitle = stringResource(R.string.credential_prompt_title)
    val credentialSubtitle = stringResource(R.string.credential_prompt_subtitle)

    PinEntryContent(
        title = stringResource(R.string.pin_unlock_title),
        subtitle = stringResource(R.string.pin_unlock_subtitle),
        pin = state.pin,
        showPin = state.showPin,
        errorText = state.error?.let {
            stringResource(
                when (it) {
                    PinUnlockError.Incorrect -> R.string.pin_error_incorrect
                    PinUnlockError.StorageFailure -> R.string.pin_error_storage
                },
            )
        },
        enabled = state.inputEnabled,
        onDigit = { viewModel.onEvent(PinUnlockEvent.DigitEntered(it)) },
        onBackspace = { viewModel.onEvent(PinUnlockEvent.Backspace) },
        onToggleShow = { viewModel.onEvent(PinUnlockEvent.ToggleShowPin) },
        modifier = modifier,
        footer = {
            when (val lockout = state.lockout) {
                LockoutUi.None -> Unit
                is LockoutUi.Waiting -> Text(
                    stringResource(
                        R.string.lockout_wait,
                        stringResource(R.string.lockout_time_format, lockout.remainingSeconds / 60, lockout.remainingSeconds % 60),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                LockoutUi.CredentialRequired -> {
                    Text(
                        stringResource(R.string.lockout_credential),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Button(
                        onClick = {
                            activity?.showDeviceCredentialPrompt(credentialTitle, credentialSubtitle) {
                                viewModel.onEvent(PinUnlockEvent.DeviceCredentialSucceeded)
                            }
                        },
                    ) { Text(stringResource(R.string.lockout_use_device_lock)) }
                }
            }
        },
    )
}
