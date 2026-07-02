package com.example.todo.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

/** Issues and verifies HS256 access tokens. */
class JwtSupport(private val config: AuthConfig) {
    private val algorithm = Algorithm.HMAC256(config.jwtSecret)

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(config.jwtIssuer)
        .withAudience(config.jwtAudience)
        .build()

    fun createAccessToken(userId: String, email: String, nowMillis: Long): String =
        JWT.create()
            .withIssuer(config.jwtIssuer)
            .withAudience(config.jwtAudience)
            .withSubject(userId)
            .withClaim(CLAIM_EMAIL, email)
            .withIssuedAt(Date(nowMillis))
            .withExpiresAt(Date(nowMillis + config.accessTtlSeconds * 1000))
            .sign(algorithm)

    companion object {
        const val CLAIM_EMAIL = "email"
    }
}
