package com.argent.http

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders

/**
 * Attaches the Bearer token when one exists, and sends no `Authorization` header
 * when it doesn't — unlike Ktor's `Auth { bearer { … } }`, whose memoised token can
 * go stale across login/logout unless explicitly cleared.
 *
 * Use it for endpoints hit both pre-login and post-login (experiment cohorts,
 * banners): the token is read fresh on every request, so a login mid-session is
 * picked up with no cache-clear dance.
 */
fun createOptionalBearerAuthPlugin(tokenStore: TokenStore) = createClientPlugin("OptionalBearerAuthPlugin") {
    onRequest { request, _ ->
        val access = tokenStore.accessToken()
        if (access.isNotBlank()) {
            request.headers[HttpHeaders.Authorization] = "Bearer $access"
        }
    }
}
