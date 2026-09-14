package com.argent.http

/**
 * The pure "what now?" answer after a refresh attempt, independent of any
 * network or storage — extracted so the full policy is unit-testable without a
 * server.
 */
sealed interface RefreshDecision {
    /** A usable token pair came back. Persist it and retry the original request. */
    data class Success(val accessToken: String, val refreshToken: String, val userId: Long) : RefreshDecision

    /** The session is unrecoverable — force logout and do not retry. */
    data class Expired(val reason: String) : RefreshDecision

    /** Transient failure (server 5xx, unexpected status). Keep the session; do not retry now. */
    data object Unavailable : RefreshDecision
}

/**
 * Map a refresh HTTP status + parsed body to a [RefreshDecision].
 *
 * The subtlety that matters in production:
 *  - 401 / 403  → the *refresh token itself* is dead → [RefreshDecision.Expired] (log the user out)
 *  - 5xx        → the *server* is sick, not the session → [RefreshDecision.Unavailable] (keep the
 *    user logged in; a logout here would be a self-inflicted outage during a backend blip)
 *  - 200 but an unparseable body → treat as [RefreshDecision.Expired] — a malformed success is
 *    indistinguishable from a broken session and is never worth silently accepting.
 */
fun decideRefresh(status: Int, body: RefreshTokenResponse?): RefreshDecision {
    if (status == 200) {
        val access = body?.accessToken.orEmpty()
        val refresh = body?.refreshToken.orEmpty()
        val userId = body?.userId ?: 0L
        return if (access.isBlank() || refresh.isBlank() || userId == 0L) {
            RefreshDecision.Expired("invalid_refresh_response")
        } else {
            RefreshDecision.Success(access, refresh, userId)
        }
    }
    return when (status) {
        401 -> RefreshDecision.Expired("refresh_token_expired")
        403 -> RefreshDecision.Expired("account_forbidden")
        in 500..599 -> RefreshDecision.Unavailable
        else -> RefreshDecision.Unavailable
    }
}
