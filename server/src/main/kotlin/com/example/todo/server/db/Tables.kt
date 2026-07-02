package com.example.todo.server.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.date
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

/**
 * A List (slice 3). [ownerId] is the single authoritative Owner: a NOT NULL
 * reference means a List can never be ownerless and never have two owners
 * (ADR-0009's "exactly one Owner" invariant is structural). Editors are held
 * separately in [Memberships] from slice 5; owner ∪ memberships = all members.
 */
object Lists : Table("lists") {
    val id = uuid("id")
    val name = varchar("name", 200)
    val ownerId = uuid("owner_id").references(Users.id)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

/**
 * A Todo within a List (slice 4). [listId] is a NOT NULL FK with CASCADE, so a
 * Todo is never orphaned and is deleted with its List. [orderKey] is a
 * fractional key for manual ordering: moving one Todo is a single-row UPDATE
 * and never requires renumbering the whole List.
 */
object Todos : Table("todos") {
    val id = uuid("id")
    val listId = uuid("list_id").references(Lists.id)
    val title = varchar("title", 500)
    val description = text("description").nullable()
    val dueDate = date("due_date").nullable()
    val completed = bool("completed").default(false)
    val orderKey = double("order_key")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}
