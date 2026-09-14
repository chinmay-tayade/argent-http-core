package com.argent.http

/**
 * Where auth tokens live between requests. The client never reads them into a
 * long-lived field — it asks [TokenStore] fresh on every request and every
 * refresh, so a logout that clears the store is immediately reflected.
 *
 * Implement with whatever your app already trusts: Android DataStore / EncryptedSharedPreferences,
 * iOS Keychain, or SQLDelight. Keep the refresh token out of plaintext storage if you can.
 */
interface TokenStore {
    /** Current access token, or "" when signed out. */
    suspend fun accessToken(): String

    /** Current refresh token, or "" when signed out. */
    suspend fun refreshToken(): String

    /** Persist a refreshed pair. [userId] lets a single store key sessions by account. */
    suspend fun save(accessToken: String, refreshToken: String, userId: Long)
}
