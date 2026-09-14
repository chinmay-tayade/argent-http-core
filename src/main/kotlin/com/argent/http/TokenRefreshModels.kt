package com.argent.http

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body sent to the refresh endpoint. */
@Serializable
data class RefreshTokenRequest(
    @SerialName("refreshToken") val refreshToken: String,
)

/** Response body returned by the refresh endpoint on a 200. */
@Serializable
data class RefreshTokenResponse(
    @SerialName("accessToken") val accessToken: String? = null,
    @SerialName("refreshToken") val refreshToken: String? = null,
    @SerialName("userId") val userId: Long? = null,
)
