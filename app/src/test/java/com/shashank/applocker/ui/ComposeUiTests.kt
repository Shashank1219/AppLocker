package com.shashank.applocker.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import com.shashank.applocker.feature.pin.LockoutUi
import com.shashank.applocker.feature.pin.PinUnlockUiState
import com.shashank.applocker.feature.pin.UnlockFooter
import com.shashank.applocker.feature.settings.SettingsContent
import com.shashank.applocker.feature.settings.SettingsEvent
import com.shashank.applocker.feature.settings.SettingsUiState
import com.shashank.applocker.security.BiometricAvailability
import com.shashank.applocker.security.FingerprintMessage
import com.shashank.applocker.ui.components.PinEntryContent
import com.shashank.applocker.ui.theme.AppLockerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Compose UI tests of the stateless screens, run on the JVM with Robolectric. They check
 * wiring, content descriptions and enabled state. They do not check pixel layout, haptics,
 * FLAG_SECURE, or whether the 360x640 PIN screen fits without scrolling (manual).
 */
@RunWith(RobolectricTestRunner::class)
class ComposeUiTests {
    @get:Rule val compose = createComposeRule()

    private fun pinScreen(pin: String = "", enabled: Boolean = true, show: Boolean = false, onDigit: (Char) -> Unit = {}, onBack: () -> Unit = {}, onToggle: () -> Unit = {}) {
        compose.setContent {
            AppLockerTheme {
                PinEntryContent(
                    title = "Enter your PIN", subtitle = "sub", pin = pin, showPin = show, errorText = null,
                    enabled = enabled, onDigit = onDigit, onBackspace = onBack, onToggleShow = onToggle,
                )
            }
        }
    }

    @Test fun keypadSendsDigitsAndBackspace() {
        val digits = mutableListOf<Char>()
        var backs = 0
        pinScreen(onDigit = { digits += it }, onBack = { backs++ })
        listOf("1", "5", "0").forEach { compose.onNodeWithText(it).performClick() }
        compose.onNodeWithContentDescription("Delete last digit").performClick()
        assertEquals(listOf('1', '5', '0'), digits)
        assertEquals(1, backs)
    }

    @Test fun keypadIsDisabledWhenInputIsDisabled() {
        pinScreen(enabled = false)
        compose.onNodeWithText("7").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Delete last digit").assertIsNotEnabled()
    }

    @Test fun dotsAnnounceOnlyTheDigitCount() {
        pinScreen(pin = "135")
        compose.onNodeWithContentDescription("3 of 6 digits entered").assertExists()
        // The digits themselves are not exposed anywhere while masked.
        compose.onAllNodesWithText("3").assertCountEquals(1) // only the keypad "3" key
    }

    @Test fun showPinToggleSwitchesLabelAndRevealsDigits() {
        var shown by mutableStateOf(false)
        compose.setContent {
            AppLockerTheme {
                PinEntryContent(
                    title = "t", subtitle = "s", pin = "135", showPin = shown, errorText = null, enabled = true,
                    onDigit = {}, onBackspace = {}, onToggleShow = { shown = !shown },
                )
            }
        }
        compose.onNodeWithText("Show PIN").performClick()
        compose.onNodeWithText("Hide PIN").assertExists()
        compose.waitForIdle()
        // The revealed digit is hidden from accessibility (count-only description), so look in the unmerged tree.
        compose.onAllNodesWithText("1", useUnmergedTree = true).assertCountEquals(2) // keypad key + revealed digit
    }

    @Test fun errorTextIsShown() {
        compose.setContent {
            AppLockerTheme {
                PinEntryContent("t", "s", "", false, "Incorrect PIN", true, {}, {}, {})
            }
        }
        compose.onNodeWithText("Incorrect PIN").assertExists()
    }

    // --- Unlock footer ---

    @Test fun fingerprintButtonOnlyWhenOffered() {
        var clicks = 0
        compose.setContent { AppLockerTheme { UnlockFooter(PinUnlockUiState(fingerprintOffered = false), { clicks++ }, {}) } }
        compose.onNodeWithText("Use fingerprint").assertDoesNotExist()
    }

    @Test fun fingerprintButtonCallsBack() {
        var clicks = 0
        compose.setContent { AppLockerTheme { UnlockFooter(PinUnlockUiState(fingerprintOffered = true), { clicks++ }, {}) } }
        compose.onNodeWithText("Use fingerprint").performClick()
        assertEquals(1, clicks)
    }

    @Test fun footerShowsFallbackMessagesAndNothingForSilentCancel() {
        compose.setContent {
            AppLockerTheme { UnlockFooter(PinUnlockUiState(fingerprintMessage = FingerprintMessage.LOCKED), {}, {}) }
        }
        compose.onNodeWithText("Fingerprint is locked right now. Use your PIN.").assertExists()
    }

    @Test fun footerShowsKeyChangedInstruction() {
        compose.setContent {
            AppLockerTheme { UnlockFooter(PinUnlockUiState(fingerprintMessage = FingerprintMessage.KEY_CHANGED), {}, {}) }
        }
        compose.onNode(hasText("Your fingerprints changed", substring = true)).assertExists()
    }

    @Test fun footerShowsCountdown() {
        compose.setContent {
            AppLockerTheme { UnlockFooter(PinUnlockUiState(lockout = LockoutUi.Waiting(75)), {}, {}) }
        }
        compose.onNodeWithText("Too many attempts. Try again in 1:15.").assertExists()
    }

    @Test fun footerShowsCredentialButtonWhenRequired() {
        var credential = 0
        compose.setContent {
            AppLockerTheme { UnlockFooter(PinUnlockUiState(lockout = LockoutUi.CredentialRequired), {}, { credential++ }) }
        }
        compose.onNodeWithText("Use device screen lock").performClick()
        assertEquals(1, credential)
    }

    // --- Settings ---

    @Test fun settingsSwitchSendsToggleEvents() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent {
            AppLockerTheme {
                SettingsContent(SettingsUiState(availability = BiometricAvailability.AVAILABLE), { events += it })
            }
        }
        compose.onNode(isToggleable()).assertIsOff().assertIsEnabled().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.ToggleFingerprint(true)), events)
    }

    @Test fun enabledSwitchIsOnAndTurnsOff() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent {
            AppLockerTheme {
                SettingsContent(SettingsUiState(fingerprintEnabled = true, availability = BiometricAvailability.AVAILABLE), { events += it })
            }
        }
        compose.onNode(isToggleable()).assertIsOn().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.ToggleFingerprint(false)), events)
    }

    @Test fun noHardwareDisablesSwitchAndExplains() {
        compose.setContent {
            AppLockerTheme { SettingsContent(SettingsUiState(availability = BiometricAvailability.NO_HARDWARE), {}) }
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("This device has no fingerprint sensor, so only the PIN is used.").assertExists()
    }

    @Test fun notEnrolledDisablesSwitchAndExplains() {
        compose.setContent {
            AppLockerTheme { SettingsContent(SettingsUiState(availability = BiometricAvailability.NOT_ENROLLED), {}) }
        }
        compose.onNode(isToggleable()).assertIsNotEnabled()
        compose.onNodeWithText("Add a fingerprint in your device settings to use this.").assertExists()
    }

    @Test fun pinConfirmationReplacesTheListAndCanBeCancelled() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent {
            AppLockerTheme {
                SettingsContent(
                    SettingsUiState(availability = BiometricAvailability.AVAILABLE, pinConfirm = com.shashank.applocker.feature.settings.PinConfirmState()),
                    { events += it },
                )
            }
        }
        compose.onNodeWithText("Confirm your PIN").assertExists()
        compose.onNodeWithText("4").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(listOf(SettingsEvent.PinDigit('4'), SettingsEvent.CancelPinConfirm), events)
    }
}
