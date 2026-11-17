# App Locker

A fully offline Android app locker. It locks apps you choose behind a 6-digit PIN or your fingerprint. Everything stays on the device: there is no backend, no account, no analytics, no ads, and the app has no network permission.

- Android 8.0 (API 26) or newer, phones, tablets and foldables, any manufacturer.
- No Google Play Services, no OEM SDK, no native libraries. One universal APK.
- Kotlin, Jetpack Compose, Material 3, MVVM, Hilt, Room, DataStore, `androidx.biometric`.

## Status

Implemented: PIN setup, change and unlock; lockout after failed attempts; fingerprint unlock bound to the Android Keystore; choosing protected apps; the accessibility-based lock screen; re-lock rules; settings; privacy notice; erase-all-data.

**Not implemented yet** (so do not rely on them): the guided first-run welcome and permission flow, the main-screen "Protection is off" card, the intruder selfie, the "Protect Settings" suggestion, and OEM battery/autostart shortcuts. See [Known limitations](#known-limitations).

Nothing in this project has been verified on a physical device yet. Unit tests run on the JVM only. Use the [manual test checklist](#manual-test-checklist) before sharing the APK.

## Build on Windows

### Requirements

| Item | Version |
| --- | --- |
| JDK | 17 or newer on `PATH` (Gradle may download a JDK 21 toolchain on its own the first time) |
| Android SDK platform | `platforms;android-36.1` (compileSdk 36.1; targetSdk 36) |
| Android SDK build-tools | `build-tools;36.1.0` (any 36.x works) |
| Android SDK platform-tools | for `adb` |
| Gradle | not needed: the wrapper downloads Gradle 9.4.1 |

Install the SDK packages with Android Studio (SDK Manager) or on the command line:

```powershell
sdkmanager "platforms;android-36.1" "build-tools;36.1.0" "platform-tools"
```

Point Gradle at the SDK by letting Android Studio create `local.properties`, or write it yourself (the file is gitignored):

```
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

### Commands

Run these from the project root in PowerShell:

```powershell
.\gradlew.bat assembleDebug          # debug APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat testDebugUnitTest      # unit tests (JVM, Robolectric)
.\gradlew.bat lintDebug              # Android lint
.\gradlew.bat assembleRelease        # minified (R8) release APK
```

A debug build needs no keystore. Library versions are pinned in `gradle/libs.versions.toml`. Some newer releases of the libraries need compileSdk 37, which is deliberately not used.

## Run on a device

1. On the phone: Settings, About phone, tap "Build number" 7 times, then Settings, Developer options, turn on "USB debugging".
2. Connect it by USB and accept the debugging prompt.
3. From the project root:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

You can also run it from Android Studio, or use an emulator at API 26 or newer. Fingerprint features need an emulator with a fingerprint enrolled, or a real device.

## First-time setup on the phone

1. Open the app and create a 6-digit PIN. Weak PINs (repeated or sequential digits) are rejected. Recovery needs a device screen lock (PIN, pattern or password) to be set on the phone.
2. Open Settings, then Protected apps, and switch on the apps to lock. Warnings appear for the home screen app, the phone app, Settings and keyboards.
3. Turn on the Accessibility service (below). Without it nothing is locked.
4. Optional: Settings, Fingerprint unlock (needs a PIN confirmation, and an enrolled fingerprint).

### Accessibility permission

The app detects which app is in front through an Accessibility service. It listens to "window changed" events only. It cannot read screen content, record input, perform gestures or use the network.

1. In the app: Settings, Protection. Read the explanation, then tap "Open Accessibility settings".
2. Find "App Locker" (often under Installed apps or Downloaded apps) and turn it on. Confirm the system dialog.
3. Go back to the app. Protection should read "Protection is on".

**Android 13 and newer, installed from an APK file:** the switch may be greyed out. Open App info (the Protection screen has a button), tap the three-dot menu, choose "Allow restricted settings", then repeat step 2.

If the phone's battery manager stops the service, the lock stops working. Allow the app to run in the background and see <https://dontkillmyapp.com> for brand-specific steps (Xiaomi, Oppo, Vivo, Realme, OnePlus, Samsung, Huawei and others).

## Release build and signing

Never commit a keystore or its passwords. `keystore.properties`, `*.jks`, `*.keystore` and `local.properties` are in `.gitignore`.

### 1. Create a keystore (once)

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\keystores"
keytool -genkeypair -v -keystore "$env:USERPROFILE\keystores\applocker-release.jks" `
  -alias applocker -keyalg RSA -keysize 4096 -validity 10000
```

Keep this file and its passwords safe and backed up. Every update must be signed with the same key, or users cannot update in place. The `applicationId` (`com.shashank.applocker`) must never change.

### 2. Tell Gradle where it is

Copy `keystore.properties.example` to `keystore.properties` in the project root and fill it in:

```
storeFile=C:/Users/<you>/keystores/applocker-release.jks
storePassword=<store password>
keyAlias=applocker
keyPassword=<key password>
```

Or use environment variables for the current PowerShell session instead:

```powershell
$env:APPLOCKER_STORE_FILE = "$env:USERPROFILE\keystores\applocker-release.jks"
$env:APPLOCKER_STORE_PASSWORD = Read-Host "Store password"
$env:APPLOCKER_KEY_ALIAS = "applocker"
$env:APPLOCKER_KEY_PASSWORD = Read-Host "Key password"
```

### 3. Build and verify

```powershell
.\gradlew.bat assembleRelease
```

The signed APK is `app\build\outputs\apk\release\app-release.apk`. Without signing material the same command still works but produces `app-release-unsigned.apk`, which cannot be installed.

Verify the signature (use your installed build-tools version):

```powershell
& "$env:ANDROID_HOME\build-tools\36.1.0\apksigner.bat" verify --verbose --print-certs app\build\outputs\apk\release\app-release.apk
```

Keep `app\build\outputs\mapping\release\mapping.txt` for each released version: it decodes crash stack traces from the minified build. Release builds are not debuggable, contain no `BuildConfig`, and have no logging. See [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md) before sharing.

## Sharing the APK with others

Each installation is independent: it generates its own random salt and keys and asks for its own PIN. There is no default PIN and no shared secret.

1. Send the signed `app-release.apk` (messaging app, cloud drive or USB).
2. On the receiving phone, open the file. Android asks to allow the installer app (Files, browser, chat app) to install unknown apps. Allow it for that app, then install.
3. **Google Play Protect** may warn that the app is unrecognised, because it is sideloaded and uses an Accessibility service. Choose "More details", then "Install anyway". This is expected for apps that are not from the Play Store.
4. Follow [First-time setup](#first-time-setup-on-the-phone), including the Android 13+ "Allow restricted settings" step.
5. Set the battery option to unrestricted/allow background activity, and the brand's autostart option if it has one (Xiaomi, Oppo, Vivo, Realme, OnePlus, Samsung, Huawei). Guide: <https://dontkillmyapp.com>.

## Permissions (final merged manifest)

| Permission | Why |
| --- | --- |
| `android.permission.USE_BIOMETRIC` | Show the system fingerprint prompt. |
| `android.permission.USE_FINGERPRINT` | Added automatically by `androidx.biometric` for API 26-27. Not declared by this app. |
| `com.shashank.applocker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Internal signature permission created by AndroidX so a screen-off receiver can be registered as not exported on older Android versions. Only this app can hold it. |

There is no `INTERNET`, no `QUERY_ALL_PACKAGES`, no storage, camera, overlay or boot permission. The app lists launchable apps through a narrow `<queries>` block (launcher, home, dialer and settings intents). The Accessibility service is declared with `BIND_ACCESSIBILITY_SERVICE`, which only the system can hold.

Components: `MainActivity` (exported, the launcher entry), `LockActivity` (not exported, excluded from Recents), and `AppLockerAccessibilityService` (exported only so the system can bind it). The library receiver `ProfileInstallReceiver` is removed. Cleartext traffic is disabled.

## Security model

**What is protected**

- **PIN:** never stored. A PBKDF2WithHmacSHA256 verifier (random 16-byte salt, iterations calibrated to about 200 ms on the device, clamped between 100,000 and 2,000,000) is encrypted with an AES-256-GCM key held in the Android Keystore. Comparison is constant-time. A wrong PIN always shows the same message.
- **Secrets storage:** the PIN verifier, the lockout state and the fingerprint token record all go through one `SecretStore` backed by the Keystore. There is no plaintext fallback: on Keystore failure the app shows an error and a retry.
- **Lockout:** 3 failures wait 30 s, 5 wait 1 min, 8 wait 5 min, and 10 or more require the device screen lock (or stay at 5 minutes if the device has none). Waits use elapsed realtime and the boot count, so changing the clock does not bypass them, and they survive process death. Nothing is wiped after failed attempts.
- **Fingerprint:** an AES-GCM Keystore key with `setUserAuthenticationRequired(true)`, per-use authentication and invalidation on new enrolment. A random token is encrypted with it. Unlock shows `BiometricPrompt` with a `CryptoObject`, decrypts the token with the authorised cipher and compares it before unlocking. A biometric success without a verified token unlocks nothing. If the key is invalidated (for example a new fingerprint is added) the key and token are deleted, fingerprint unlock switches off, and the user re-enables it with the PIN. Only Strong biometrics are accepted. No biometric data is stored.
- **Unlock state** lives in memory only. Process death, a service restart or a reboot locks everything again.
- **Screens:** `FLAG_SECURE` is set on the PIN, setup, change-PIN, settings, app-selection and lock screens. The lock screen filters touches while another window covers it.
- **Backups:** `allowBackup="false"`, with data-extraction and backup rules that exclude everything.
- **Logging:** none in the app. Package names, PINs and keys are never logged.
- **Protected app names** are stored unencrypted in Room. This is deliberate: package names are not secret. The privacy notice in Settings says so.

**What a determined attacker could still do**

- Someone who can open Settings (and the app is not itself protected) can switch the Accessibility service off, force-stop or uninstall the app.
- With root access, a debugger on a debuggable build, or a compromised Keystore, protections can be bypassed.
- Recents thumbnails and notification content of protected apps are not hidden.
- Detection is event-driven, so a protected app may flash for a moment on a slow device before the lock appears.
- The Keystore is only as strong as the device's hardware (some phones store keys in software).

## Privacy

Everything stays on the device. The app has no network permission, no analytics, no accounts and no ads, and nothing leaves the device through it.

## Manual test checklist

Run what is possible and note what was not run. Use devices or emulators at API 26, 28, 30, 34 and the latest, with at least one that has no fingerprint enrolled and one that has.

1. **Fresh install:** create a PIN (weak PINs are rejected, mismatched confirmation is rejected), reach Settings, then enable the Accessibility service, including "Allow restricted settings" on Android 13+.
2. **PIN screen:** keypad works, haptics, show/hide, a wrong PIN shows only "Incorrect PIN". It fits a 360x640dp screen without scrolling. TalkBack announces only the digit count.
3. **Lockout:** 3, 5, 8 failures give the right waits; 10 asks for the device screen lock. Change the system clock during a wait and reboot during a wait: the wait must not shorten.
4. **Fingerprint:** enabling needs the PIN; cancelling or "Use PIN" does not count as a failure. Add a new fingerprint: unlock falls back to PIN, fingerprint switches off with a message, and it can be re-enabled with the PIN. Remove all fingerprints: same. With no fingerprint enrolled the option is disabled with an explanation. Try more than one manufacturer.
5. **Locking:** the lock appears the first time a protected app opens, after screen off, after reboot and after each re-lock delay (immediately, 30 s, 1 min, 5 min, screen off). Check Recents, Home, the notification shade, the keyboard and the volume panel (they must not relock or unlock anything unexpectedly). Cancel or Back on the lock screen returns to Home.
6. **Two protected apps:** unlock one, then open the other. Unlocking one must not unlock the other, and the lock must not loop.
7. **Settings locked:** lock the Settings app and confirm you cannot reach Accessibility settings without unlocking.
8. **Service off:** switch the service off; Settings, Protection shows "Protection is off". Turn it on; it recovers.
9. **Change PIN:** needs the current PIN; the old PIN stops working; fingerprint still works.
10. **Erase all data:** wrong PIN stops it; right PIN then the second confirmation erases everything and the app restarts at PIN setup, with apps no longer locked.
11. **Upgrade:** install a newer build over an older one signed with the same key. PIN and selections must remain.
12. **Process death:** force-stop the app while an app is unlocked; it must be locked again when reopened.
13. **Lock screen launch:** check that the service can start the lock screen over a protected app on each device. If a device blocks it, the service sends the user Home instead; record the device.
14. **Layout:** phone, tablet and foldable; portrait and landscape; large font; dark and light theme; dynamic colour on Android 12+.
15. **Observed delay:** note how long the lock screen takes to appear after a protected app opens.

## Known limitations

- Recents thumbnails of protected apps cannot be hidden by this app.
- Notification content from protected apps is still visible.
- The app can be force-stopped or uninstalled by someone who can open Settings, unless Settings is locked (do protect the Settings app).
- Some manufacturers kill background services aggressively. Behaviour depends on the phone's battery settings.
- Split-screen, picture-in-picture, work profile, Private Space and secondary users are not covered.
- Lock detection is event-driven, so a very short flash of the protected app may be visible on slow devices.
- Keyboards are never locked: the service ignores input-method windows by design, even if one is selected.
- The lock screen is started by the Accessibility service. Android normally allows this, but a device may block it; the service then sends the user Home. This is untested on real devices.
- Deleting this app's data from system settings, or erasing it, removes the PIN and the lock.
- Fingerprint behaviour (Keystore enforcement, exceptions and invalidation) differs between manufacturers and API 26-29 versus 30+. It must be checked on real devices.
- Not built yet: first-run guided setup, the main-screen status card, intruder selfie, "Protect Settings" suggestion, OEM autostart shortcuts, and reconciling uninstalled apps when the service starts (it runs when the picker opens).
- Library versions are held back so everything compiles against SDK 36.1.
