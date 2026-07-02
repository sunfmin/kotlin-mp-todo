package com.example.todo.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Users : Table("users") {
    val id = uuid("id")
    val email = varchar("email", 320)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object OtpCodes : Table("otp_codes") {
    val email = varchar("email", 320)
    val codeHash = varchar("code_hash", 128)
    val expiresAt = timestamp("expires_at")
    val attempts = integer("attempts")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(email)
}

object RefreshTokens : Table("refresh_tokens") {
    val tokenHash = varchar("token_hash", 128)
    val userId = uuid("user_id").references(Users.id)
    val expiresAt = timestamp("expires_at")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(tokenHash)
}

object OtpRequests : Table("otp_requests") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 320)
    val requestedAt = timestamp("requested_at")
    override val primaryKey = PrimaryKey(id)
}
