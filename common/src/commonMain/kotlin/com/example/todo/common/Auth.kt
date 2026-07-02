package com.example.todo.common

import kotlinx.serialization.Serializable

/** Generic error body returned by the server for non-2xx responses. */
@Serializable
data class ApiError(val message: String)

@Serializable
data class RequestOtpRequest(val email: String)

/**
 * Response to an OTP request. [devCode] is populated only when the server is
 * configured to expose codes (dev/test); it is null in production.
 */
@Serializable
data class RequestOtpResponse(val devCode: String? = null)

@Serializable
data class VerifyOtpRequest(val email: String, val code: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

/** Tokens issued on successful sign-in or refresh (ADR-0003). */
@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
    val userId: String,
    val email: String,
)

/** Identity of the authenticated user (from the bearer access token). */
@Serializable
data class MeResponse(val userId: String, val email: String)
