package com.argent.http

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProviders
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * A production Ktor [HttpClient] factory centred on one hard problem: **token refresh**.
 *
 * The interesting part is not "attach a bearer header" — it is *what happens when the
 * access token is rejected*, and how carefully each failure is classified:
 *
 *  - `401` on the refresh call → the refresh token itself is dead → force logout.
 *  - `403` → account suspended/forbidden → force logout.
 *  - `5xx` / network error → the *server* is sick, not the session → keep the user
 *    logged in (a logout here is a self-inflicted outage during a backend blip).
 *  - `200` with an unparseable body → treat as a broken session.
 *
 * The refresh *decision* lives in [decideRefresh] as a pure function, so the whole
 * policy is unit-tested without a server; this class only wires it to Ktor.
 */
class HttpClientFactory(
    private val serverConfig: ServerConfig,
    private val tokens: TokenStore,
    private val authEvents: AuthEvents,
) {
    private val tokenClearCallbacks = mutableListOf<() -> Unit>()

    private val refreshEndpoint = "${serverConfig.authBaseUrl}auth/v1/refresh-token"

    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        encodeDefaults = true
    }

    /** Force every authenticated client to drop its cached token (call on logout). */
    fun clearAllBearerTokenCaches() {
        tokenClearCallbacks.forEach { runCatching { it() } }
        Log.d("Cleared ${tokenClearCallbacks.size} bearer token caches")
    }

    /** Shared config for every REST client: JSON, timeouts, logging, base URL. */
    private fun HttpClientConfig<*>.installBaseConfig(baseUrl: String) {
        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            socketTimeoutMillis = 20_000L
            requestTimeoutMillis = 20_000L
            connectTimeoutMillis = 10_000L
        }

        if (serverConfig.debugLoggingEnabled) {
            install(Logging) {
                level = LogLevel.ALL
                logger = object : Logger {
                    override fun log(message: String) = Log.d(message)
                }
                sanitizeHeader { it == "Authorization" }
            }
        }

        defaultRequest {
            url(baseUrl)
            contentType(ContentType.Application.Json)
        }
    }

    /**
     * Fully authenticated client: bearer token on every request, with refresh-on-401
     * and the careful failure classification described above.
     */
    fun createAuthenticated(baseUrl: String): HttpClient = HttpClient(CIO) {
        installBaseConfig(baseUrl)

        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }

        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokens.accessToken()
                    if (access.isBlank()) null
                    else BearerTokens(access, tokens.refreshToken())
                }

                refreshTokens {
                    val accessToken = tokens.accessToken()
                    val currentRefresh = oldTokens?.refreshToken?.takeIf { it.isNotBlank() }
                    if (currentRefresh == null) {
                        Log.w("refreshTokens → no refresh token, expiring session")
                        authEvents.onSessionExpired("no_refresh_token")
                        return@refreshTokens null
                    }

                    Log.d("refreshTokens → attempting refresh")
                    val refreshClient = HttpClient(CIO) {
                        install(ContentNegotiation) { json(json) }
                        if (serverConfig.debugLoggingEnabled) {
                            install(Logging) {
                                level = LogLevel.ALL
                                logger = object : Logger {
                                    override fun log(message: String) = Log.d(message)
                                }
                                sanitizeHeader { it == "Authorization" }
                            }
                        }
                    }

                    try {
                        val response = refreshClient.post(refreshEndpoint) {
                            contentType(ContentType.Application.Json)
                            headers { append("Authorization", "Bearer $accessToken") }
                            setBody(RefreshTokenRequest(currentRefresh))
                        }

                        when (val decision = decideRefresh(
                            status = response.status.value,
                            body = runCatching { response.body<RefreshTokenResponse>() }.getOrNull(),
                        )) {
                            is RefreshDecision.Success -> {
                                tokens.save(decision.accessToken, decision.refreshToken, decision.userId)
                                Log.d("refreshTokens → success, tokens saved")
                                BearerTokens(decision.accessToken, decision.refreshToken)
                            }

                            is RefreshDecision.Expired -> {
                                Log.e("refreshTokens → ${decision.reason}")
                                authEvents.onSessionExpired(decision.reason)
                                null
                            }

                            RefreshDecision.Unavailable -> {
                                Log.w("refreshTokens → server unavailable, keeping session")
                                null
                            }
                        }
                    } catch (e: Exception) {
                        // Network error (timeout, no connectivity) — do NOT expire the session.
                        Log.e("refreshTokens → network error: ${e.message}")
                        null
                    } finally {
                        refreshClient.close()
                    }
                }

                sendWithoutRequest { true }
            }
        }
    }.also { client ->
        client.authProviders
            .filterIsInstance<BearerAuthProvider>()
            .forEach { provider -> tokenClearCallbacks.add { provider.clearToken() } }
    }

    /** No auth, no retry — callers own their error handling (login, OTP, public endpoints). */
    fun createUnauthenticated(baseUrl: String): HttpClient = HttpClient(CIO) {
        installBaseConfig(baseUrl)
    }

    /** Token attached when present, absent when not — for endpoints hit pre- and post-login. */
    fun createOptionallyAuthenticated(baseUrl: String): HttpClient = HttpClient(CIO) {
        installBaseConfig(baseUrl)
        install(createOptionalBearerAuthPlugin(tokens))
    }
}
