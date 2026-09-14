package com.argent.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RefreshDecisionTest {

    private fun resp(access: String? = "new-access", refresh: String? = "new-refresh", userId: Long? = 42L) =
        RefreshTokenResponse(accessToken = access, refreshToken = refresh, userId = userId)

    @Test fun `200 with a valid body succeeds`() {
        val decision = decideRefresh(200, resp())
        val success = assertIs<RefreshDecision.Success>(decision)
        assertEquals("new-access", success.accessToken)
        assertEquals("new-refresh", success.refreshToken)
        assertEquals(42L, success.userId)
    }

    @Test fun `200 with a blank access token is expired`() {
        val decision = decideRefresh(200, resp(access = ""))
        assertIs<RefreshDecision.Expired>(decision)
    }

    @Test fun `200 with a null body is expired`() {
        assertIs<RefreshDecision.Expired>(decideRefresh(200, null))
    }

    @Test fun `200 with a missing userId is expired`() {
        assertIs<RefreshDecision.Expired>(decideRefresh(200, resp(userId = null)))
    }

    @Test fun `401 means the refresh token itself is dead`() {
        val decision = decideRefresh(401, null)
        assertIs<RefreshDecision.Expired>(decision)
        assertEquals("refresh_token_expired", decision.reason)
    }

    @Test fun `403 means forbidden`() {
        assertIs<RefreshDecision.Expired>(decideRefresh(403, null))
    }

    @Test fun `5xx keeps the session — do not log the user out during a backend blip`() {
        assertIs<RefreshDecision.Unavailable>(decideRefresh(500, null))
        assertIs<RefreshDecision.Unavailable>(decideRefresh(503, null))
    }

    @Test fun `unexpected statuses are unavailable rather than expired`() {
        assertIs<RefreshDecision.Unavailable>(decideRefresh(302, null))
        assertIs<RefreshDecision.Unavailable>(decideRefresh(418, null))
    }
}
