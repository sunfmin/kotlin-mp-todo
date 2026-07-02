package com.example.todo.server.auth

import java.security.MessageDigest
import java.security.SecureRandom

internal object Crypto {
    private val random = SecureRandom()

    fun sha256Hex(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.encodeToByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 6-digit numeric OTP, zero-padded. */
    fun newOtpCode(): String = "%06d".format(random.nextInt(1_000_000))

    /** URL-safe opaque token for refresh tokens. */
    fun newOpaqueToken(): String {
        val buf = ByteArray(32)
        random.nextBytes(buf)
        return buf.joinToString("") { "%02x".format(it) }
    }
}
