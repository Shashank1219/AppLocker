# Release checklist

Tick every item before sending an APK to anyone. Items marked (device) need a real phone or emulator and cannot be proven by the unit tests.

## Before building

- [ ] `applicationId` is still `com.shashank.applocker` (it must never change).
- [ ] `versionCode` is higher than the last shared build, and `versionName` is updated in `app/build.gradle.kts`.
- [ ] Room schema changed? A new migration exists, is registered in `ALL_MIGRATIONS`, and `MigrationTest` covers it. The new `app/schemas/.../<n>.json` is committed.
- [ ] `git status` shows no keystore, `keystore.properties` or `local.properties` staged.
- [ ] You have the release keystore and its passwords backed up somewhere safe. The same key must sign every update.

## Build and automated checks

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleRelease
```

- [ ] All four commands succeed. Tests: 0 failed.
- [ ] Lint has no errors.
- [ ] `app\build\outputs\apk\release\app-release.apk` exists (not `-unsigned`).
- [ ] `apksigner verify --verbose --print-certs` reports a valid signature with your release certificate.
- [ ] `mapping.txt` from `app\build\outputs\mapping\release\` is saved with this version number.

## Manifest and permissions

Inspect `app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml`.

- [ ] Permissions are only `USE_BIOMETRIC`, `USE_FINGERPRINT` (from androidx.biometric) and `com.shashank.applocker.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
- [ ] No `INTERNET`, no `QUERY_ALL_PACKAGES`, no storage, camera or overlay permission.
- [ ] `android:debuggable` is not `true`, `allowBackup="false"`, `usesCleartextTraffic="false"`.
- [ ] Exported components: only `MainActivity` and the Accessibility service (protected by `BIND_ACCESSIBILITY_SERVICE`). No exported receiver or provider.
- [ ] `git grep -nE "Log\.|println|printStackTrace" -- app/src/main` finds nothing.
- [ ] No `BuildConfig` is generated.

## Security checks

- [ ] No PIN, token or key appears in source, logs or resources.
- [ ] Backup and data-extraction rules still exclude everything.
- [ ] `FLAG_SECURE` is on PIN setup, unlock, change PIN, settings, app selection and the lock screen.

## Device checks (device)

Run the manual test checklist in the README, on at least:

- [ ] One device or emulator at API 26-29 and one at API 30 or newer.
- [ ] One with a fingerprint enrolled and one without.
- [ ] A fresh install of the signed release APK (not the debug build): PIN setup, enabling the service, locking an app, unlocking by PIN and by fingerprint.
- [ ] Two different phone brands if you can, to check lock launch from the service, the system UI package, and battery/autostart behaviour.
- [ ] An upgrade install over the previous release keeps the PIN and protected apps.
- [ ] The release build starts and works with R8 on: every screen opens, the lock screen shows, nothing crashes.

## Sharing

- [ ] The recipient knows about: allow installs from the sending app, the Google Play Protect "Install anyway" prompt, "Allow restricted settings" on Android 13+, enabling the Accessibility service, and the battery/autostart settings.
- [ ] You told them the app is not from the Play Store and cannot be restored if the PIN and device screen lock are both forgotten (erase the app data to start over).
- [ ] Known limitations in the README were read and accepted.
