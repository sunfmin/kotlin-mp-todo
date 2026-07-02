package com.example.todo.server.auth

/**
 * Auth tuning. [otpReturnInResponse] exposes the generated code in the API
 * response for dev/test only (there is no real email delivery here); it must be
 * false in production.
 */
data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String = "kotlin-mp-todo",
    val jwtAudience: String = "kotlin-mp-todo-clients",
    val accessTtlSeconds: Long = 900,          // 15 minutes
    val refreshTtlSeconds: Long = 60L * 60 * 24 * 30, // 30 days
    val otpTtlSeconds: Long = 300,             // 5 minutes
    val maxOtpAttempts: Int = 5,
    val rateLimitWindowSeconds: Long = 60,
    val rateLimitMaxRequests: Int = 5,
    val otpReturnInResponse: Boolean = false,
)

/** Thrown by the auth service for expected failures; mapped to HTTP by StatusPages. */
class AuthException(val kind: Kind, message: String) : RuntimeException(message) {
    enum class Kind { INVALID_CODE, UNAUTHORIZED, RATE_LIMITED, BAD_REQUEST }
}
