package com.example.todo.server.auth

import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TokenResponse
import com.example.todo.server.db.OtpCodes
import com.example.todo.server.db.OtpRequests
import com.example.todo.server.db.RefreshTokens
import com.example.todo.server.db.Users
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.util.UUID

/**
 * Passwordless OTP auth (ADR-0003). The server owns OTP generation, expiry,
 * attempt limits, and rate limiting. Codes are stored hashed; refresh tokens are
 * opaque and stored hashed.
 */
class AuthService(
    private val config: AuthConfig,
    private val jwt: JwtSupport,
    private val clock: () -> Instant = { Clock.System.now() },
) {
    private val log = LoggerFactory.getLogger(AuthService::class.java)

    fun requestOtp(email: String): RequestOtpResponse {
        val normalized = normalizeEmail(email)
        val now = clock()
        return transaction {
            val windowStart = now.plus(-config.rateLimitWindowSeconds, DateTimeUnit.SECOND)
            val recent = OtpRequests.selectAll()
                .where { (OtpRequests.email eq normalized) and (OtpRequests.requestedAt greaterEq windowStart) }
                .count()
            if (recent >= config.rateLimitMaxRequests) {
                throw AuthException(AuthException.Kind.RATE_LIMITED, "Too many code requests; try again later.")
            }
            OtpRequests.insert { it[OtpRequests.email] = normalized; it[requestedAt] = now }

            val code = Crypto.newOtpCode()
            OtpCodes.deleteWhere { OtpCodes.email eq normalized }
            OtpCodes.insert {
                it[OtpCodes.email] = normalized
                it[codeHash] = hashCode(normalized, code)
                it[expiresAt] = now.plus(config.otpTtlSeconds, DateTimeUnit.SECOND)
                it[attempts] = 0
                it[createdAt] = now
            }
            log.info("OTP for {} is {} (dev delivery)", normalized, code)
            RequestOtpResponse(devCode = if (config.otpReturnInResponse) code else null)
        }
    }

    fun verifyOtp(email: String, code: String): TokenResponse {
        val normalized = normalizeEmail(email)
        val now = clock()
        return transaction {
            val row = OtpCodes.selectAll().where { OtpCodes.email eq normalized }.singleOrNull()
                ?: throw AuthException(AuthException.Kind.INVALID_CODE, "No code was requested for this email.")

            if (row[OtpCodes.expiresAt] < now) {
                OtpCodes.deleteWhere { OtpCodes.email eq normalized }
                throw AuthException(AuthException.Kind.INVALID_CODE, "The code has expired.")
            }
            val attempts = row[OtpCodes.attempts]
            if (attempts >= config.maxOtpAttempts) {
                OtpCodes.deleteWhere { OtpCodes.email eq normalized }
                throw AuthException(AuthException.Kind.RATE_LIMITED, "Too many attempts; request a new code.")
            }
            if (row[OtpCodes.codeHash] != hashCode(normalized, code)) {
                OtpCodes.update({ OtpCodes.email eq normalized }) { it[OtpCodes.attempts] = attempts + 1 }
                throw AuthException(AuthException.Kind.INVALID_CODE, "Incorrect code.")
            }
            OtpCodes.deleteWhere { OtpCodes.email eq normalized }
            val userId = findOrCreateUser(normalized, now)
            issueTokens(userId, normalized, now)
        }
    }

    fun refresh(refreshToken: String): TokenResponse {
        val now = clock()
        val hash = Crypto.sha256Hex(refreshToken)
        return transaction {
            val row = RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq hash }.singleOrNull()
                ?: throw AuthException(AuthException.Kind.UNAUTHORIZED, "Invalid refresh token.")
            if (row[RefreshTokens.expiresAt] < now) {
                RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq hash }
                throw AuthException(AuthException.Kind.UNAUTHORIZED, "Refresh token expired.")
            }
            val userId = row[RefreshTokens.userId]
            val email = Users.selectAll().where { Users.id eq userId }.single()[Users.email]
            // Rotate: invalidate the used refresh token, issue a fresh pair.
            RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq hash }
            issueTokens(userId, email, now)
        }
    }

    fun signOut(refreshToken: String) {
        val hash = Crypto.sha256Hex(refreshToken)
        transaction { RefreshTokens.deleteWhere { RefreshTokens.tokenHash eq hash } }
    }

    private fun issueTokens(userId: UUID, email: String, now: Instant): TokenResponse {
        val access = jwt.createAccessToken(userId.toString(), email, now.toEpochMilliseconds())
        val refresh = Crypto.newOpaqueToken()
        RefreshTokens.insert {
            it[tokenHash] = Crypto.sha256Hex(refresh)
            it[RefreshTokens.userId] = userId
            it[expiresAt] = now.plus(config.refreshTtlSeconds, DateTimeUnit.SECOND)
            it[createdAt] = now
        }
        return TokenResponse(
            accessToken = access,
            refreshToken = refresh,
            accessTokenExpiresInSeconds = config.accessTtlSeconds,
            userId = userId.toString(),
            email = email,
        )
    }

    private fun findOrCreateUser(email: String, now: Instant): UUID {
        val existing = Users.selectAll().where { Users.email eq email }.singleOrNull()
        if (existing != null) return existing[Users.id]
        val id = UUID.randomUUID()
        Users.insert {
            it[Users.id] = id
            it[Users.email] = email
            it[createdAt] = now
        }
        return id
    }

    private fun hashCode(email: String, code: String): String =
        Crypto.sha256Hex("$email:$code:${config.jwtSecret}")

    private fun normalizeEmail(email: String): String {
        val normalized = email.trim().lowercase()
        if (!normalized.contains("@") || normalized.length < 3) {
            throw AuthException(AuthException.Kind.BAD_REQUEST, "Enter a valid email address.")
        }
        return normalized
    }
}
