package com.argent.http

/**
 * Base URLs and endpoints for the services this client talks to. Supplied by
 * your DI layer (a flavor / BuildConfig / xcconfig swap), never hardcoded.
 */
data class ServerConfig(
    /** Root of the auth service; the refresh endpoint is `<authBaseUrl>auth/v1/refresh-token`. */
    val authBaseUrl: String,
    /** Optional: turn on wire logging (Authorization header always redacted). */
    val debugLoggingEnabled: Boolean = false,
)
