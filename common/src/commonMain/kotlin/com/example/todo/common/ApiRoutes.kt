package com.example.todo.common

/** API paths shared between server and clients so the contract has a single source of truth. */
object ApiRoutes {
    const val HEALTH = "/health"

    // Auth (slice 2)
    const val AUTH_OTP_REQUEST = "/auth/otp/request"
    const val AUTH_OTP_VERIFY = "/auth/otp/verify"
    const val AUTH_TOKEN_REFRESH = "/auth/token/refresh"
    const val AUTH_SIGNOUT = "/auth/signout"
    const val ME = "/me"
}
