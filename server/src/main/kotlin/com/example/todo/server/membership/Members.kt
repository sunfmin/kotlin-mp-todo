package com.example.todo.server.membership

import com.example.todo.common.Role
import com.example.todo.server.DomainException
import com.example.todo.server.db.Lists
import com.example.todo.server.db.Memberships
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import java.util.UUID

/**
 * Central membership authorization (slice 5). One place decides who counts as a
 * member of a List and in what [Role], so todos, membership, and later slices
 * all authorize the same way. Every function must run inside a transaction.
 *
 * A List's Owner is [Lists.ownerId]; every other member is an EDITOR row in
 * [Memberships]. Non-members get a 404 (never 403) so a List's existence is not
 * leaked; a member with too low a role gets a 403.
 */
object Members {

    /** The caller's role in the List, or null if they are not a member. */
    fun roleOf(userId: UUID, listId: UUID): String? {
        val owner = Lists.select(Lists.ownerId).where { Lists.id eq listId }
            .firstOrNull()?.get(Lists.ownerId) ?: return null
        if (owner == userId) return Role.OWNER
        val isEditor = Memberships.selectAll()
            .where { (Memberships.listId eq listId) and (Memberships.userId eq userId) }
            .any()
        return if (isEditor) Role.EDITOR else null
    }

    /** Owner or editor may proceed; non-members see a 404. Returns the role. */
    fun requireMember(userId: UUID, listId: UUID): String =
        roleOf(userId, listId) ?: throw DomainException.notFound("List not found.")

    /** Only the Owner may proceed: 404 for non-members, 403 for editors. */
    fun requireOwner(userId: UUID, listId: UUID) {
        when (roleOf(userId, listId)) {
            Role.OWNER -> Unit
            null -> throw DomainException.notFound("List not found.")
            else -> throw DomainException.forbidden("Only the List owner can do that.")
        }
    }
}
