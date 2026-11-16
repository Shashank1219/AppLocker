# Biometric-bound key: design and what can be tested

## Design

- One AES-256-GCM key lives in AndroidKeyStore only (`AndroidBiometricKeyStore`). It requires
  user authentication for **every** operation (no validity window; `AUTH_BIOMETRIC_STRONG` on
  API 30+) and is invalidated when a fingerprint is added. Key bytes are never read by the app.
- A random 32-byte token is generated at enable time, encrypted with that key, and stored as
  IV + ciphertext + SHA-256(token) in `SecretStore` (a second, non-auth-bound Keystore layer).
  The plaintext token exists only briefly in memory and is wiped.
- Unlock decrypts the ciphertext with a cipher that BiometricPrompt authorised, hashes the
  result and compares in constant time. Only `UnlockTokenResult.Verified` may unlock.
- Because the key needs authentication for encryption too, enabling also needs a fingerprint
  prompt: `prepareEnrollment` -> prompt -> `completeEnrollment`.
- Stage 6 uses it as: `prepare*` returns an initialised `Cipher`; wrap it in
  `BiometricPrompt.CryptoObject(cipher)`; in `onAuthenticationSucceeded` pass the same cipher
  to `complete*`.

## What the unit tests cover (run on the JVM)

`BiometricKeyManagerTest`, `BiometricPrimitivesTest`, `CryptoErrorsTest`:

- Enroll then unlock round trip with a software AES key; repeated unlocks; re-enrolment.
- Token never appears in stored bytes; token array is wiped; record layout and parsing,
  including malformed input.
- Tampered ciphertext, wrong expected digest, corrupt record: all fail closed.
- Invalidated key, key dropped by the system, no enrolled fingerprint, provider failure,
  secure-storage failure mid-enrolment: each maps to the right `CryptoFailure`, and
  invalidation deletes key and token.
- Mapping of platform exceptions to `CryptoFailure` (Robolectric is used only to construct
  the Android exception classes).

## What they cannot cover (needs a real device or emulator)

A software key does not enforce authentication and is not AndroidKeyStore, so these are
**not tested by anything automated** and are on the manual checklist:

1. That the key really requires a fingerprint for each use, including encryption.
2. Which exception the platform throws when a cipher is used without authentication
   (`UserNotAuthenticatedException` at init vs. at `doFinal`, and on which API levels).
   The mapper handles the former; the latter is unverified.
3. That adding a fingerprint invalidates the key, and whether a given device throws
   `KeyPermanentlyInvalidatedException` or silently deletes the key. Both are handled, but
   per-device behaviour is unverified.
4. `InvalidAlgorithmParameterException` when no fingerprint is enrolled at key creation.
5. API 26-29 (`setUserAuthenticationValidityDurationSeconds(-1)`) vs API 30+
   (`setUserAuthenticationParameters`) behaviour.
6. Key generation time and Keystore quirks on OEM devices (`ProviderException`, etc.).
7. The BiometricPrompt + CryptoObject hand-off itself (Stage 6).

Manual checks: enable on a device with a fingerprint; unlock several times; add a fingerprint
and confirm unlock fails with `KEY_INVALIDATED` and the key and token are gone; try enabling
with no fingerprint enrolled.
