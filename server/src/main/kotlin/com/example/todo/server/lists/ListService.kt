package com.example.todo.server.lists

import com.example.todo.common.ListDto
import com.example.todo.common.Role
import com.example.todo.server.DomainException
import com.example.todo.server.db.Lists
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
 * Lists owned by a User (slice 3). Every endpoint authorizes against ownership:
 * a User sees and mutates only Lists they own. Ownership is the `owner_id` column
 * (single source of truth for the "exactly one Owner" invariant, ADR-0009).
 */
class ListService(private val clock: () -> Instant = { Clock.System.now() }) {

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

    /** Lists the User owns, newest first. */
    fun listFor(ownerId: UUID): List<ListDto> = transaction {
        Lists.selectAll().where { Lists.ownerId eq ownerId }
            .orderBy(Lists.createdAt to org.jetbrains.exposed.sql.SortOrder.DESC)
            .map { it.toDto() }
    }

    fun get(userId: UUID, listId: UUID): ListDto = transaction {
        requireOwned(userId, listId).toDto()
    }

    fun rename(userId: UUID, listId: UUID, name: String): ListDto = transaction {
        val cleanName = validateName(name)
        requireOwned(userId, listId)
        Lists.update({ Lists.id eq listId }) { it[Lists.name] = cleanName }
        Lists.selectAll().where { Lists.id eq listId }.single().toDto()
    }

    fun delete(userId: UUID, listId: UUID): Unit = transaction {
        requireOwned(userId, listId)
        Lists.deleteWhere { Lists.id eq listId }
    }

    /** Loads the List and fails with 404 (never 403, to avoid leaking existence). */
    private fun requireOwned(userId: UUID, listId: UUID): ResultRow {
        val row = Lists.selectAll().where { Lists.id eq listId }.singleOrNull()
            ?: throw DomainException.notFound("List not found.")
        if (row[Lists.ownerId] != userId) throw DomainException.notFound("List not found.")
        return row
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) throw DomainException.badRequest("List name is required.")
        if (trimmed.length > 200) throw DomainException.badRequest("List name is too long.")
        return trimmed
    }

    private fun ResultRow.toDto() = ListDto(
        id = this[Lists.id].toString(),
        name = this[Lists.name],
        role = Role.OWNER,
        createdAt = this[Lists.createdAt].toString(),
    )
}
