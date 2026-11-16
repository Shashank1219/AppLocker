package com.shashank.applocker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shashank.applocker.R
import com.shashank.applocker.domain.model.PIN_LENGTH

private val KeyHeight = 56.dp

/**
 * Shared PIN screen body. Sized to fit 360x640dp without scrolling. Dots announce only the
 * number of digits to TalkBack, never the digits themselves.
 */
@Composable
fun PinEntryContent(
    title: String,
    subtitle: String,
    pin: String,
    showPin: Boolean,
    errorText: String?,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onToggleShow: () -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            PinDisplay(pin = pin, showPin = showPin)
            Box(Modifier.height(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (errorText != null) {
                    Text(
                        errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
            TextButton(onClick = onToggleShow, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (showPin) R.string.pin_hide else R.string.pin_show))
            }
            footer()
        }
        PinKeypad(enabled = enabled, onDigit = onDigit, onBackspace = onBackspace)
    }
}

@Composable
private fun PinDisplay(pin: String, showPin: Boolean) {
    val description = stringResource(R.string.pin_digits_entered, pin.length, PIN_LENGTH)
    Row(
        modifier = Modifier
            .height(40.dp)
            .clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < pin.length
            if (showPin && filled) {
                Text(pin[index].toString(), style = MaterialTheme.typography.headlineMedium)
            } else {
                Box(
                    Modifier
                        .size(if (filled) 16.dp else 12.dp)
                        .background(
                            if (filled) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
fun PinKeypad(
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val backspaceDescription = stringResource(R.string.pin_backspace_description)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit ->
                    KeyButton(label = digit.toString(), enabled = enabled, modifier = Modifier.weight(1f)) {
                        haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                        onDigit(digit)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            KeyButton(label = "0", enabled = enabled, modifier = Modifier.weight(1f)) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onDigit('0')
            }
            KeyButton(
                label = "⌫",
                enabled = enabled,
                description = backspaceDescription,
                modifier = Modifier.weight(1f),
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onBackspace()
            }
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(KeyHeight).semantics { description?.let { contentDescription = it } },
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}
