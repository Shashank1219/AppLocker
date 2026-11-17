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
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onFirst
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

    @Test fun relockOptionsAreListedAndSelectable() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent { AppLockerTheme { SettingsContent(SettingsUiState(availability = BiometricAvailability.AVAILABLE), { events += it }) } }
        compose.onNodeWithText("After 1 minute").performScrollTo().performClick()
        assertEquals(SettingsEvent.SetRelockDelay(com.shashank.applocker.domain.model.RelockDelay.AFTER_1_MINUTE), events.single())
    }

    @Test fun lockAllButtonSendsEvent() {
        val events = mutableListOf<SettingsEvent>()
        compose.setContent { AppLockerTheme { SettingsContent(SettingsUiState(availability = BiometricAvailability.AVAILABLE), { events += it }) } }
        compose.onNodeWithText("Lock all apps now").performScrollTo().performClick()
        assertEquals(listOf<SettingsEvent>(SettingsEvent.LockAllNow), events)
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

    // --- App selection ---

    private fun item(pkg: String, label: String, selected: Boolean = false, role: com.shashank.applocker.domain.apps.CriticalRole? = null, available: Boolean = true) =
        com.shashank.applocker.domain.apps.AppItem(pkg, label, false, selected, role, available)

    private fun appsScreen(state: com.shashank.applocker.feature.apps.AppSelectionUiState, events: MutableList<com.shashank.applocker.feature.apps.AppSelectionEvent> = mutableListOf()) {
        compose.setContent {
            AppLockerTheme { com.shashank.applocker.feature.apps.AppSelectionContent(state, { events += it }) }
        }
    }

    @Test fun appRowShowsLabelAndPackageAndTogglesSelection() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, items = listOf(item("com.x.cam", "Camera"))), events)
        compose.onNodeWithText("com.x.cam").assertExists()
        compose.onNodeWithText("Camera").performClick()
        assertEquals(listOf<com.shashank.applocker.feature.apps.AppSelectionEvent>(com.shashank.applocker.feature.apps.AppSelectionEvent.Toggle("com.x.cam", true)), events)
    }

    @Test fun selectedAppShowsAsOnAndTogglesOff() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, items = listOf(item("a", "Alpha", selected = true))), events)
        compose.onAllNodes(isToggleable() and hasText("Alpha")).onFirst().assertIsOn().performClick()
        assertEquals(com.shashank.applocker.feature.apps.AppSelectionEvent.Toggle("a", false), events.single())
    }

    @Test fun typingInSearchSendsQuery() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, items = listOf(item("a", "Alpha"))), events)
        compose.onNodeWithText("Search apps").performTextInput("zo")
        assertEquals(com.shashank.applocker.feature.apps.AppSelectionEvent.QueryChanged("zo"), events.last())
    }

    @Test fun emptyListShowsEmptyState() {
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false))
        compose.onNodeWithText("No apps found.").assertExists()
    }

    @Test fun emptySearchResultShowsNoMatchState() {
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, query = "zzz"))
        compose.onNodeWithText("No apps match your search.").assertExists()
    }

    @Test fun errorStateOffersRetry() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, error = true), events)
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf<com.shashank.applocker.feature.apps.AppSelectionEvent>(com.shashank.applocker.feature.apps.AppSelectionEvent.Retry), events)
    }

    @Test fun warningDialogConfirmsOrCancels() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        val warn = item("p", "Phone", role = com.shashank.applocker.domain.apps.CriticalRole.PHONE)
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, items = listOf(warn), warning = warn), events)
        compose.onNodeWithText("Protect Phone?").assertExists()
        compose.onNodeWithText("Protect anyway").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(
            listOf(com.shashank.applocker.feature.apps.AppSelectionEvent.ConfirmWarning, com.shashank.applocker.feature.apps.AppSelectionEvent.DismissWarning),
            events,
        )
    }

    @Test fun unavailableAppIsExplainedAndOnlyRemovable() {
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false, items = listOf(item("gone", "gone", selected = true, available = false))))
        compose.onNodeWithText("Not available. It may be disabled or uninstalled.").assertExists()
    }

    // --- Protection ---

    private fun protection(
        status: com.shashank.applocker.feature.protection.ProtectionStatus = com.shashank.applocker.feature.protection.ProtectionStatus.OFF,
        restricted: Boolean = false,
        onSettings: () -> Unit = {},
        onInfo: () -> Unit = {},
    ) = compose.setContent {
        AppLockerTheme {
            com.shashank.applocker.feature.protection.ProtectionContent(
                com.shashank.applocker.feature.protection.ProtectionUiState(status), restricted, onSettings, onInfo,
            )
        }
    }

    @Test fun protectionShowsDisclosureAndOpensSettings() {
        var opened = 0
        protection(onSettings = { opened++ })
        compose.onNodeWithText("Protection is off").assertExists()
        compose.onNode(hasText("does not read anything on your screen", substring = true)).assertExists()
        compose.onNodeWithText("Open Accessibility settings").performClick()
        assertEquals(1, opened)
        compose.onNodeWithText("Open App info").assertDoesNotExist()
    }

    @Test fun restrictedSettingsHelpShowsWhenOffAndApplicable() {
        var info = 0
        protection(restricted = true, onInfo = { info++ })
        compose.onNodeWithText("Open App info").performScrollTo().performClick()
        assertEquals(1, info)
    }

    @Test fun protectionOnHidesRestrictedHelp() {
        protection(com.shashank.applocker.feature.protection.ProtectionStatus.ON, restricted = true)
        compose.onNodeWithText("Protection is on").assertExists()
        compose.onNodeWithText("Open App info").assertDoesNotExist()
    }

    @Test fun showSystemAppsSwitchSendsEvent() {
        val events = mutableListOf<com.shashank.applocker.feature.apps.AppSelectionEvent>()
        appsScreen(com.shashank.applocker.feature.apps.AppSelectionUiState(loading = false), events)
        compose.onNodeWithText("Show system apps").performClick()
        assertEquals(com.shashank.applocker.feature.apps.AppSelectionEvent.ShowSystemApps(true), events.single())
    }
}
