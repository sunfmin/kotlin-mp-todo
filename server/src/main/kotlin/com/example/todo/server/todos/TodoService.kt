package com.example.todo.server.todos

import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ReorderTodoRequest
import com.example.todo.common.TodoDto
import com.example.todo.common.UpdateTodoRequest
import com.example.todo.server.DomainException
import com.example.todo.server.db.Lists
import com.example.todo.server.db.Todos
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Todos within a List (slice 4). Every operation first authorizes that the
 * caller owns the List — a user can only see and mutate Todos in Lists they
 * own. Ordering uses a fractional [Todos.orderKey]: appending picks the next
 * integer, and a move averages the neighbours' keys, so a reorder is always a
 * single-row UPDATE.
 */
class TodoService(private val clock: () -> Instant = { Clock.System.now() }) {

    fun create(userId: UUID, listId: UUID, req: CreateTodoRequest): TodoDto = transaction {
        requireOwned(userId, listId)
        val cleanTitle = validateTitle(req.title)
        val cleanDesc = req.description?.trim()?.takeIf(String::isNotEmpty)
        val cleanDue = req.dueDate?.takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val id = UUID.randomUUID()
        val now = clock()
        val nextOrder = (Todos.selectAll().where { Todos.listId eq listId }
            .maxOfOrNull { it[Todos.orderKey] } ?: 0.0) + 1.0
        Todos.insert {
            it[Todos.id] = id
            it[Todos.listId] = listId
            it[Todos.title] = cleanTitle
            it[Todos.description] = cleanDesc
            it[Todos.dueDate] = cleanDue
            it[Todos.completed] = false
            it[Todos.orderKey] = nextOrder
            it[createdAt] = now
        }
        id.toDto()
    }

    /** Todos in the List, ordered by [Todos.orderKey] ascending. */
    fun listForList(userId: UUID, listId: UUID): List<TodoDto> = transaction {
        requireOwned(userId, listId)
        Todos.selectAll().where { Todos.listId eq listId }
            .orderBy(Todos.orderKey to org.jetbrains.exposed.sql.SortOrder.ASC)
            .map { it.toDto() }
    }

    fun get(userId: UUID, listId: UUID, todoId: UUID): TodoDto = transaction {
        requireOwned(userId, listId)
        requireTodo(listId, todoId).toDto()
    }

    fun update(userId: UUID, listId: UUID, todoId: UUID, req: UpdateTodoRequest): TodoDto =
        transaction {
            requireOwned(userId, listId)
            requireTodo(listId, todoId)
            val cleanDesc = req.description?.trim()?.takeIf(String::isNotEmpty)
            val cleanDue = req.dueDate?.takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            Todos.update({ Todos.id eq todoId }) {
                req.title?.let { t -> it[Todos.title] = validateTitle(t) }
                if (req.description != null) {
                    it[Todos.description] = cleanDesc
                }
                if (req.dueDate != null) {
                    it[Todos.dueDate] = cleanDue
                }
                req.completed?.let { c -> it[Todos.completed] = c }
            }
            Todos.selectAll().where { Todos.id eq todoId }.single().toDto()
        }

    fun delete(userId: UUID, listId: UUID, todoId: UUID): Unit = transaction {
        requireOwned(userId, listId)
        requireTodo(listId, todoId)
        Todos.deleteWhere { Todos.id eq todoId }
    }

    fun reorder(userId: UUID, listId: UUID, todoId: UUID, req: ReorderTodoRequest): TodoDto =
        transaction {
            requireOwned(userId, listId)
            requireTodo(listId, todoId)

            // Current ordered Todos (excluding the one being moved).
            val others = Todos.selectAll().where { Todos.listId eq listId and (Todos.id neq todoId) }
                .orderBy(Todos.orderKey to org.jetbrains.exposed.sql.SortOrder.ASC)
                .map { it[Todos.id] to it[Todos.orderKey] }

            val newKey = if (req.beforeId == null) {
                // Move to the end.
                (others.lastOrNull()?.second ?: 0.0) + 1.0
            } else {
                val targetId = runCatching { UUID.fromString(req.beforeId) }.getOrNull()
                    ?: throw DomainException.badRequest("Invalid beforeId.")
                val targetIndex = others.indexOfFirst { it.first == targetId }
                if (targetIndex < 0) throw DomainException.notFound("Target Todo not found.")
                val prev = if (targetIndex > 0) others[targetIndex - 1].second else null
                val next = others[targetIndex].second
                if (prev == null) next - 1.0 else (prev + next) / 2.0
            }

            Todos.update({ Todos.id eq todoId }) { it[Todos.orderKey] = newKey }
            Todos.selectAll().where { Todos.id eq todoId }.single().toDto()
        }

    private fun requireOwned(userId: UUID, listId: UUID) {
        val row = Lists.selectAll().where { Lists.id eq listId }.singleOrNull()
            ?: throw DomainException.notFound("List not found.")
        if (row[Lists.ownerId] != userId) throw DomainException.notFound("List not found.")
    }

    private fun requireTodo(listId: UUID, todoId: UUID): ResultRow =
        Todos.selectAll().where { Todos.id eq todoId and (Todos.listId eq listId) }.singleOrNull()
            ?: throw DomainException.notFound("Todo not found.")

    private fun validateTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) throw DomainException.badRequest("Todo title is required.")
        if (trimmed.length > 500) throw DomainException.badRequest("Todo title is too long.")
        return trimmed
    }

    private fun ResultRow.toDto() = TodoDto(
        id = this[Todos.id].toString(),
        listId = this[Todos.listId].toString(),
        title = this[Todos.title],
        description = this[Todos.description],
        dueDate = this[Todos.dueDate]?.toString(),
        completed = this[Todos.completed],
        order = this[Todos.orderKey],
        createdAt = this[Todos.createdAt].toString(),
    )

    private fun UUID.toDto(): TodoDto =
        Todos.selectAll().where { Todos.id eq this@toDto }.single().toDto()
}
