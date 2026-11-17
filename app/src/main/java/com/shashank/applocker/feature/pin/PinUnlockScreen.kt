package com.shashank.applocker.feature.pin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.feature.biometric.showFingerprintPrompt
import com.shashank.applocker.security.FingerprintMessage
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
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    val credentialTitle = stringResource(R.string.credential_prompt_title)
    val credentialSubtitle = stringResource(R.string.credential_prompt_subtitle)
    val fpTitle = stringResource(R.string.fingerprint_prompt_title)
    val fpSubtitle = stringResource(R.string.fingerprint_prompt_subtitle)
    val fpNegative = stringResource(R.string.fingerprint_prompt_negative)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PinUnlockEffect.Unlocked -> onUnlocked()
                PinUnlockEffect.RecoveryAuthorized -> onRecoveryAuthorized()
                is PinUnlockEffect.ShowFingerprintPrompt -> activity?.showFingerprintPrompt(
                    cipher = effect.cipher,
                    title = fpTitle,
                    subtitle = fpSubtitle,
                    negativeText = fpNegative,
                    onSuccess = { viewModel.onEvent(PinUnlockEvent.FingerprintSucceeded(it)) },
                    onError = { viewModel.onEvent(PinUnlockEvent.FingerprintError(it)) },
                ) ?: viewModel.onEvent(PinUnlockEvent.FingerprintError(-1))
            }
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(PinUnlockEvent.Refresh) }
    // One automatic prompt when the screen first shows with fingerprint on; the button covers the rest.
    LaunchedEffect(state.fingerprintOffered) {
        if (state.fingerprintOffered) viewModel.onEvent(PinUnlockEvent.FingerprintRequested(automatic = true))
    }

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
            UnlockFooter(
                state = state,
                onFingerprint = { viewModel.onEvent(PinUnlockEvent.FingerprintRequested()) },
                onDeviceCredential = {
                    activity?.showDeviceCredentialPrompt(credentialTitle, credentialSubtitle) {
                        viewModel.onEvent(PinUnlockEvent.DeviceCredentialSucceeded)
                    }
                },
            )
        },
    )
}

/** Lockout countdown, device-credential recovery and the fingerprint offer. Stateless. */
@Composable
fun UnlockFooter(
    state: PinUnlockUiState,
    onFingerprint: () -> Unit,
    onDeviceCredential: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                Button(onClick = onDeviceCredential, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.lockout_use_device_lock))
                }
            }
        }
        fingerprintMessageRes(state.fingerprintMessage)?.let {
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (state.fingerprintOffered) {
            OutlinedButton(onClick = onFingerprint, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.fingerprint_use))
            }
        }
    }
}

fun fingerprintMessageRes(message: FingerprintMessage): Int? = when (message) {
    FingerprintMessage.NONE -> null
    FingerprintMessage.LOCKED -> R.string.fingerprint_msg_locked
    FingerprintMessage.UNAVAILABLE -> R.string.fingerprint_msg_unavailable
    FingerprintMessage.NOT_ENROLLED -> R.string.fingerprint_msg_not_enrolled
    FingerprintMessage.FAILED -> R.string.fingerprint_msg_failed
    FingerprintMessage.KEY_CHANGED -> R.string.fingerprint_msg_key_changed
}
