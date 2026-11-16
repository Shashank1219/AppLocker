package com.shashank.applocker.security

/**
 * Encrypted key/value storage for secrets. Implementations must never fall back to plaintext.
 * Calls may block (Keystore, disk); call from a background dispatcher.
 */
interface SecretStore {
    /** @throws SecretStoreException if the value could not be encrypted and stored. */
    fun put(key: String, value: ByteArray)

    /** @return the value, or null if [key] was never stored. @throws SecretStoreException on failure. */
    fun get(key: String): ByteArray?

    fun remove(key: String)
}

/** Any Keystore, crypto or storage failure. Deliberately carries no message or cause details. */
class SecretStoreException : Exception("Secure storage unavailable")
