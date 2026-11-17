package com.shashank.applocker.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.R
import com.shashank.applocker.domain.apps.AppItem
import com.shashank.applocker.domain.apps.CriticalRole
import com.shashank.applocker.ui.components.SecureWindow

@Composable
fun AppSelectionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppSelectionViewModel = hiltViewModel(),
) {
    SecureWindow() // The list shows which apps are protected.
    val state = viewModel.state.collectAsStateWithLifecycle().value
    AppSelectionContent(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        icon = { AppIcon(it) },
    )
}

/** Stateless picker UI. */
@Composable
fun AppSelectionContent(
    state: AppSelectionUiState,
    onEvent: (AppSelectionEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    icon: @Composable (String) -> Unit = {},
) {
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.apps_back))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.apps_selected_count, state.selectedCount),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.apps_title), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(AppSelectionEvent.QueryChanged(it)) },
            label = { Text(stringResource(R.string.apps_search)) },
            singleLine = true,
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    TextButton(onClick = { onEvent(AppSelectionEvent.QueryChanged("")) }) {
                        Text(stringResource(R.string.apps_search_clear))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .toggleable(
                    value = state.showSystemApps,
                    role = Role.Switch,
                    onValueChange = { onEvent(AppSelectionEvent.ShowSystemApps(it)) },
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.apps_show_system), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = state.showSystemApps, onCheckedChange = null)
        }
        if (state.saveFailed) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.apps_save_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onEvent(AppSelectionEvent.DismissSaveError) }) { Text(stringResource(R.string.settings_cancel)) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> Centered {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.apps_loading), modifier = Modifier.padding(top = 12.dp))
                }
                state.error -> Centered {
                    Text(stringResource(R.string.apps_error), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = { onEvent(AppSelectionEvent.Retry) }, modifier = Modifier.padding(top = 12.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.apps_retry))
                    }
                }
                state.items.isEmpty() -> Centered {
                    Text(
                        stringResource(if (state.query.isBlank()) R.string.apps_empty else R.string.apps_no_match),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.items, key = { it.packageName }) { AppRow(it, onEvent, icon) }
                }
            }
        }
    }
    state.warning?.let { WarningDialog(it, onEvent) }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun AppRow(item: AppItem, onEvent: (AppSelectionEvent) -> Unit, icon: @Composable (String) -> Unit) {
    // A missing app can only be removed from the selection, never added.
    val enabled = item.available || item.selected
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(
                value = item.selected,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = { onEvent(AppSelectionEvent.Toggle(item.packageName, it)) },
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp)) { if (item.available) icon(item.packageName) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(item.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                item.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.available) {
                Text(stringResource(R.string.apps_unavailable), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = item.selected, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun WarningDialog(item: AppItem, onEvent: (AppSelectionEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(AppSelectionEvent.DismissWarning) },
        title = { Text(stringResource(R.string.apps_warning_title, item.label)) },
        text = {
            Text(
                stringResource(
                    when (item.role) {
                        CriticalRole.LAUNCHER -> R.string.apps_warning_launcher
                        CriticalRole.PHONE -> R.string.apps_warning_phone
                        CriticalRole.SETTINGS -> R.string.apps_warning_settings
                        CriticalRole.KEYBOARD, null -> R.string.apps_warning_keyboard
                    },
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(AppSelectionEvent.ConfirmWarning) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.apps_warning_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(AppSelectionEvent.DismissWarning) }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.apps_warning_cancel))
            }
        },
    )
}
