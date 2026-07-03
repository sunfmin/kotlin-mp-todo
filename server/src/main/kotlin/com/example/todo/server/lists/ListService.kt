package com.example.todo.server.lists

import com.example.todo.common.ListDto
import com.example.todo.common.Role
import com.example.todo.server.DomainException
import com.example.todo.server.db.Lists
import com.example.todo.server.db.Memberships
import com.example.todo.server.membership.Members
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Lists a User can access (slice 3, extended for sharing in slice 5). A User sees
 * the Lists they OWN plus the Lists they've joined as an EDITOR; each [ListDto]
 * carries the caller's [Role]. Ownership is `owner_id` (single source of truth
 * for the "exactly one Owner" invariant, ADR-0009); rename/delete stay owner-only.
 */
class ListService(
    private val clock: () -> Instant = { Clock.System.now() },
    /** Called with a List id after a change, so subscribers can be notified (slice 8). */
    private val notify: (UUID) -> Unit = {},
) {

    fun create(ownerId: UUID, name: String): ListDto = transaction {
        val cleanName = validateName(name)
        val id = UUID.randomUUID()
        val now = clock()
        Lists.insert {
            it[Lists.id] = id
            it[Lists.name] = cleanName
            it[Lists.ownerId] = ownerId
            it[createdAt] = now
        }
        ListDto(id.toString(), cleanName, Role.OWNER, now.toString())
    }

    /** Lists the User owns or has joined, newest first. */
    fun listFor(userId: UUID): List<ListDto> = transaction {
        val owned = Lists.selectAll().where { Lists.ownerId eq userId }
            .map { ListDto(it[Lists.id].toString(), it[Lists.name], Role.OWNER, it[Lists.createdAt].toString()) }
        val joined = (Memberships innerJoin Lists).selectAll()
            .where { Memberships.userId eq userId }
            .map { ListDto(it[Lists.id].toString(), it[Lists.name], Role.EDITOR, it[Lists.createdAt].toString()) }
        // ISO-8601 instants sort lexicographically by time; newest first.
        (owned + joined).sortedByDescending { it.createdAt }
    }

    /** A single List the caller is a member of, with their role; 404 otherwise. */
    fun get(userId: UUID, listId: UUID): ListDto = transaction {
        val role = Members.requireMember(userId, listId)
        Lists.selectAll().where { Lists.id eq listId }.single().toDto(role)
    }

    fun rename(userId: UUID, listId: UUID, name: String): ListDto = transaction {
        val cleanName = validateName(name)
        Members.requireOwner(userId, listId)
        Lists.update({ Lists.id eq listId }) { it[Lists.name] = cleanName }
        notify(listId)
        Lists.selectAll().where { Lists.id eq listId }.single().toDto(Role.OWNER)
    }

    fun delete(userId: UUID, listId: UUID): Unit = transaction {
        Members.requireOwner(userId, listId)
        Lists.deleteWhere { Lists.id eq listId }
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw DomainException.badRequest("List name is required.")
        if (trimmed.length > 200) throw DomainException.badRequest("List name is too long.")
        return trimmed
    }

    private fun ResultRow.toDto(role: String) = ListDto(
        id = this[Lists.id].toString(),
        name = this[Lists.name],
        role = role,
        createdAt = this[Lists.createdAt].toString(),
    )
}
