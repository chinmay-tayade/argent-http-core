package com.argent.http

/**
 * Hooks the client calls when a session must end. Wire these to your app's
 * logout / event-bus so every client that detects an unrecoverable 401/403
 * funnels into a single sign-out path.
 */
interface AuthEvents {
    /**
     * The refresh token was rejected (401), the account was forbidden (403), or a
     * 200 refresh returned a body that didn't parse into a usable token pair.
     * [reason] is a stable, non-user-facing token for logs/analytics only.
     */
    suspend fun onSessionExpired(reason: String)
}
