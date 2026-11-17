package com.shashank.applocker.feature.protection

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R

@Composable
fun ProtectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProtectionViewModel = hiltViewModel(),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    val context = LocalContext.current
    // The user toggles the service in system settings, so check again when they come back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    ProtectionContent(
        state = state,
        showRestrictedHelp = Build.VERSION.SDK_INT >= 33,
        onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
        onOpenAppInfo = { openAppDetails(context) },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless UI. The disclosure is shown before the user is sent to system settings. */
@Composable
fun ProtectionContent(
    state: ProtectionUiState,
    showRestrictedHelp: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.apps_back)) }
        Text(stringResource(R.string.protection_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            stringResource(
                when (state.status) {
                    ProtectionStatus.OFF -> R.string.protection_status_off
                    ProtectionStatus.STARTING -> R.string.protection_status_starting
                    ProtectionStatus.ON -> R.string.protection_status_on
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            color = if (state.status == ProtectionStatus.ON) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(stringResource(R.string.protection_why), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.protection_does_heading), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.protection_does), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.protection_doesnt_heading), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(R.string.protection_doesnt), style = MaterialTheme.typography.bodyMedium)
        Button(
            onClick = onOpenAccessibilitySettings,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) { Text(stringResource(R.string.protection_open_settings)) }
        if (showRestrictedHelp && state.status == ProtectionStatus.OFF) {
            Text(stringResource(R.string.protection_restricted_heading), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.protection_restricted_steps), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onOpenAppInfo, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(R.string.protection_open_app_info))
            }
        }
    }
}

/** Opens system accessibility settings. Falls back to this app's info screen if a device lacks it. */
private fun openAccessibilitySettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        openAppDetails(context)
    } catch (_: SecurityException) {
        openAppDetails(context)
    }
}

private fun openAppDetails(context: Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    } catch (_: Exception) {
        // Nothing more to try. The explanation on screen still tells the user where to go.
    }
}
