package com.shashank.applocker.feature.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.domain.model.RelockDelay
import com.shashank.applocker.feature.biometric.showFingerprintPrompt
import com.shashank.applocker.feature.pin.fingerprintMessageRes
import com.shashank.applocker.feature.protection.ProtectionStatus
import com.shashank.applocker.feature.protection.ProtectionViewModel
import com.shashank.applocker.security.BiometricAvailability
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.components.SecureWindow
import com.shashank.applocker.ui.components.findActivity

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onOpenApps: () -> Unit = {},
    onOpenProtection: () -> Unit = {},
    onChangePin: () -> Unit = {},
    onWiped: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    protectionViewModel: ProtectionViewModel = hiltViewModel(),
) {
    SecureWindow()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val protection by protectionViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context.findActivity() as? FragmentActivity
    val title = stringResource(R.string.fingerprint_enroll_title)
    val subtitle = stringResource(R.string.fingerprint_prompt_subtitle)
    val negative = stringResource(R.string.fingerprint_prompt_negative)
    val versionLabel = remember(context) { versionLabel(context) }

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
                SettingsEffect.Wiped -> onWiped()
            }
        }
    }
    // The user may enrol a fingerprint or switch the service on in system settings and come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onEvent(SettingsEvent.Refresh)
        protectionViewModel.refresh()
    }

    SettingsContent(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        protectionStatus = protection.status,
        versionLabel = versionLabel,
        onOpenApps = onOpenApps,
        onOpenProtection = onOpenProtection,
        onChangePin = onChangePin,
    )
}

private fun versionLabel(context: Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    context.getString(R.string.settings_version, info.versionName.orEmpty(), PackageInfoCompat.getLongVersionCode(info))
} catch (_: Exception) {
    ""
}

/** Stateless settings UI. */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    protectionStatus: ProtectionStatus = ProtectionStatus.OFF,
    versionLabel: String = "",
    onOpenApps: () -> Unit = {},
    onOpenProtection: () -> Unit = {},
    onChangePin: () -> Unit = {},
) {
    val confirm = state.pinConfirm
    if (confirm != null) {
        PinEntryContent(
            title = stringResource(R.string.settings_confirm_pin_title),
            subtitle = stringResource(
                if (confirm.purpose == ConfirmPurpose.WIPE) R.string.settings_wipe_pin_subtitle else R.string.settings_confirm_pin_subtitle,
            ),
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

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })

        Section(R.string.settings_section_protection)
        NavRow(
            title = stringResource(R.string.settings_open_protection),
            summary = stringResource(
                when (protectionStatus) {
                    ProtectionStatus.OFF -> R.string.protection_status_off
                    ProtectionStatus.STARTING -> R.string.protection_status_starting
                    ProtectionStatus.ON -> R.string.protection_status_on
                },
            ),
            onClick = onOpenProtection,
        )
        NavRow(stringResource(R.string.settings_open_apps), stringResource(R.string.settings_open_apps_summary), onOpenApps)
        Text(stringResource(R.string.settings_relock_title), style = MaterialTheme.typography.titleMedium)
        Column(Modifier.selectableGroup()) {
            RelockDelay.entries.forEach { delay ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = state.relockDelay == delay,
                        role = Role.RadioButton,
                        onClick = { onEvent(SettingsEvent.SetRelockDelay(delay)) },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.relockDelay == delay, onClick = null)
                    Text(stringResource(relockLabel(delay)), Modifier.padding(start = 16.dp))
                }
            }
        }
        Button(onClick = { onEvent(SettingsEvent.LockAllNow) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.settings_lock_all))
        }

        Section(R.string.settings_section_security)
        NavRow(stringResource(R.string.settings_change_pin), stringResource(R.string.settings_change_pin_summary), onChangePin)
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
                Text(stringResource(R.string.settings_fingerprint), style = MaterialTheme.typography.titleMedium)
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
            Text(
                stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.message == SettingsMessage.WipeIncomplete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Section(R.string.settings_section_privacy)
        Text(stringResource(R.string.privacy_notice), style = MaterialTheme.typography.bodyMedium)

        Section(R.string.settings_section_about)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
        if (versionLabel.isNotEmpty()) Text(versionLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Section(R.string.settings_section_danger)
        Text(stringResource(R.string.settings_wipe_summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { onEvent(SettingsEvent.WipeRequested) },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.settings_wipe)) }
    }

    if (state.confirmWipe) WipeDialog(wiping = state.wiping, onEvent = onEvent)
}

@Composable
private fun Section(titleRes: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp).semantics { heading() },
    )
}

@Composable
private fun NavRow(title: String, summary: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The second, explicit confirmation. Only reachable after the PIN was entered. */
@Composable
private fun WipeDialog(wiping: Boolean, onEvent: (SettingsEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsEvent.DismissWipe) },
        title = { Text(stringResource(R.string.wipe_dialog_title)) },
        text = {
            if (wiping) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 16.dp))
                    Text(stringResource(R.string.wipe_in_progress))
                }
            } else {
                Text(stringResource(R.string.wipe_dialog_body))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(SettingsEvent.ConfirmWipe) },
                enabled = !wiping,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.wipe_dialog_confirm), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsEvent.DismissWipe) }, enabled = !wiping, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

private fun relockLabel(delay: RelockDelay): Int = when (delay) {
    RelockDelay.IMMEDIATELY -> R.string.relock_immediately
    RelockDelay.AFTER_30_SECONDS -> R.string.relock_30s
    RelockDelay.AFTER_1_MINUTE -> R.string.relock_1m
    RelockDelay.AFTER_5_MINUTES -> R.string.relock_5m
    RelockDelay.ON_SCREEN_OFF -> R.string.relock_screen_off
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
    SettingsMessage.AllLocked -> R.string.settings_all_locked
    SettingsMessage.WipeIncomplete -> R.string.settings_wipe_incomplete
    is SettingsMessage.Fingerprint -> fingerprintMessageRes(message.message) ?: R.string.settings_enroll_failed
}
