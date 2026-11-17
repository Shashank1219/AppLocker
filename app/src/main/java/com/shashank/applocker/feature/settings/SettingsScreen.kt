package com.shashank.applocker.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.feature.biometric.showFingerprintPrompt
import com.shashank.applocker.feature.pin.fingerprintMessageRes
import com.shashank.applocker.security.BiometricAvailability
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.components.SecureWindow
import com.shashank.applocker.ui.components.findActivity

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenApps: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current.findActivity() as? FragmentActivity
    val title = stringResource(R.string.fingerprint_enroll_title)
    val subtitle = stringResource(R.string.fingerprint_prompt_subtitle)
    val negative = stringResource(R.string.fingerprint_prompt_negative)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SettingsEffect.ShowEnrollPrompt -> activity?.showFingerprintPrompt(
                    cipher = effect.cipher,
                    title = title,
                    subtitle = subtitle,
                    negativeText = negative,
                    onSuccess = { viewModel.onEvent(SettingsEvent.EnrollmentAuthenticated(it)) },
                    onError = { viewModel.onEvent(SettingsEvent.EnrollmentError(it)) },
                ) ?: viewModel.onEvent(SettingsEvent.EnrollmentError(-1))
            }
        }
    }
    // The user may enrol a fingerprint in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(SettingsEvent.Refresh) }

    SettingsContent(state = state, onEvent = viewModel::onEvent, onOpenApps = onOpenApps, modifier = modifier)
}

/** Stateless settings UI. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenApps: () -> Unit = {},
) {
    val confirm = state.pinConfirm
    if (confirm != null) {
        PinEntryContent(
            title = stringResource(R.string.settings_confirm_pin_title),
            subtitle = stringResource(R.string.settings_confirm_pin_subtitle),
            pin = confirm.pin,
            showPin = false,
            errorText = confirm.error?.let {
                stringResource(
                    when (it) {
                        PinConfirmError.INCORRECT -> R.string.pin_error_incorrect
                        PinConfirmError.LOCKED -> R.string.settings_pin_locked
                        PinConfirmError.STORAGE -> R.string.pin_error_storage
                    },
                )
            },
            enabled = !confirm.isVerifying,
            onDigit = { onEvent(SettingsEvent.PinDigit(it)) },
            onBackspace = { onEvent(SettingsEvent.PinBackspace) },
            onToggleShow = {},
            modifier = modifier,
            footer = {
                TextButton(onClick = { onEvent(SettingsEvent.CancelPinConfirm) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
        return
    }

    val switchLabel = stringResource(R.string.settings_fingerprint)
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onOpenApps),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.settings_open_apps), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_open_apps_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(
                    value = state.fingerprintEnabled,
                    enabled = state.fingerprintToggleEnabled,
                    role = Role.Switch,
                    onValueChange = { onEvent(SettingsEvent.ToggleFingerprint(it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(switchLabel, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_fingerprint_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.fingerprintEnabled, onCheckedChange = null, enabled = state.fingerprintToggleEnabled)
        }
        availabilityText(state)?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        messageText(state.message)?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun availabilityText(state: SettingsUiState): Int? =
    if (state.fingerprintEnabled) null else when (state.availability) {
        BiometricAvailability.AVAILABLE -> null
        BiometricAvailability.NO_HARDWARE -> R.string.settings_fp_no_hardware
        BiometricAvailability.NOT_ENROLLED -> R.string.settings_fp_not_enrolled
        BiometricAvailability.TEMPORARILY_UNAVAILABLE -> R.string.settings_fp_unavailable
        BiometricAvailability.UPDATE_REQUIRED -> R.string.settings_fp_update
        BiometricAvailability.UNSUPPORTED -> R.string.settings_fp_unsupported
    }

private fun messageText(message: SettingsMessage?): Int? = when (message) {
    null -> null
    SettingsMessage.FingerprintEnabled -> R.string.settings_fp_enabled
    SettingsMessage.FingerprintDisabled -> R.string.settings_fp_disabled
    is SettingsMessage.Fingerprint -> fingerprintMessageRes(message.message) ?: R.string.settings_enroll_failed
}
