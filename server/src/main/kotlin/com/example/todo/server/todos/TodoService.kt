package com.example.todo.server.todos

import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ReorderTodoRequest
import com.example.todo.common.TodoDto
import com.example.todo.common.UpdateTodoRequest
import com.example.todo.server.DomainException
import com.example.todo.server.db.Todos
import com.example.todo.server.db.Users
import com.example.todo.server.membership.Members
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.max
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Todos within a List (slice 4, extended with assignment in slice 6). Every
 * operation authorizes that the caller is a member of the List (owner or editor,
 * via [Members]). Ordering uses a fractional [Todos.orderKey]: appending picks
 * the next integer and a move averages the neighbours' keys, so a reorder is
 * always a single-row UPDATE. An assignee, when set, must be a current member of
 * the Todo's List (ADR-0009).
 */
class TodoService(private val clock: () -> Instant = { Clock.System.now() }) {

    fun create(userId: UUID, listId: UUID, req: CreateTodoRequest): TodoDto = transaction {
        Members.requireMember(userId, listId)
        val cleanTitle = validateTitle(req.title)
        val cleanDesc = req.description?.trim()?.takeIf(String::isNotEmpty)
        val cleanDue = req.dueDate?.let { parseDueDate(it) }
        val id = UUID.randomUUID()
        val now = clock()
        // Let the DB compute the max order key so adding a Todo doesn't load the
        // whole List into memory.
        val maxKey = Todos.orderKey.max()
        val nextOrder = (Todos.select(maxKey).where { Todos.listId eq listId }
            .firstOrNull()?.get(maxKey) ?: 0.0) + 1.0
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
        TodoDto(
            id = id.toString(),
            listId = listId.toString(),
            title = cleanTitle,
            description = cleanDesc,
            dueDate = cleanDue?.toString(),
            completed = false,
            order = nextOrder,
            createdAt = now.toString(),
        )
    }

    /** Todos in the List, ordered by [Todos.orderKey] ascending. */
    fun listForList(userId: UUID, listId: UUID): List<TodoDto> = transaction {
        Members.requireMember(userId, listId)
        val rows = Todos.selectAll().where { Todos.listId eq listId }
            .orderBy(Todos.orderKey to org.jetbrains.exposed.sql.SortOrder.ASC)
            .toList()
        // Resolve assignee emails in one query rather than per row.
        val emails = emailsFor(rows.mapNotNull { it[Todos.assigneeId] }.toSet())
        rows.map { it.toDto(assigneeEmail = emails[it[Todos.assigneeId]]) }
    }

    fun get(userId: UUID, listId: UUID, todoId: UUID): TodoDto = transaction {
        Members.requireMember(userId, listId)
        val row = requireTodo(listId, todoId)
        row.toDto(assigneeEmail = emailOf(row[Todos.assigneeId]))
    }

    /** Assign the Todo to a member of its List, or unassign it (assigneeUserId null). */
    fun assign(userId: UUID, listId: UUID, todoId: UUID, assigneeUserId: String?): TodoDto =
        transaction {
            Members.requireMember(userId, listId)
            val old = requireTodo(listId, todoId)
            val assignee = assigneeUserId?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                val id = runCatching { UUID.fromString(raw) }.getOrNull()
                    ?: throw DomainException.badRequest("Invalid assignee id.")
                if (Members.roleOf(id, listId) == null) {
                    throw DomainException.badRequest("The assignee must be a member of this List.")
                }
                id
            }
            Todos.update({ Todos.id eq todoId }) { it[Todos.assigneeId] = assignee }
            TodoDto(
                id = todoId.toString(),
                listId = listId.toString(),
                title = old[Todos.title],
                description = old[Todos.description],
                dueDate = old[Todos.dueDate]?.toString(),
                completed = old[Todos.completed],
                order = old[Todos.orderKey],
                createdAt = old[Todos.createdAt].toString(),
                assigneeUserId = assignee?.toString(),
                assigneeEmail = emailOf(assignee),
            )
        }

    fun update(userId: UUID, listId: UUID, todoId: UUID, req: UpdateTodoRequest): TodoDto =
        transaction {
            Members.requireMember(userId, listId)
            val old = requireTodo(listId, todoId)
            // A null field means "leave unchanged"; a present-but-blank field
            // means "clear it" (so a user can remove a description or due date).
            val reqDesc = req.description
            val reqDue = req.dueDate
            val newTitle = req.title?.let { validateTitle(it) } ?: old[Todos.title]
            val newDesc =
                if (reqDesc != null) reqDesc.trim().takeIf(String::isNotEmpty)
                else old[Todos.description]
            val newDue =
                if (reqDue != null) parseDueDate(reqDue)
                else old[Todos.dueDate]
            val newCompleted = req.completed ?: old[Todos.completed]
            Todos.update({ Todos.id eq todoId }) {
                it[Todos.title] = newTitle
                it[Todos.description] = newDesc
                it[Todos.dueDate] = newDue
                it[Todos.completed] = newCompleted
            }
            TodoDto(
                id = todoId.toString(),
                listId = listId.toString(),
                title = newTitle,
                description = newDesc,
                dueDate = newDue?.toString(),
                completed = newCompleted,
                order = old[Todos.orderKey],
                createdAt = old[Todos.createdAt].toString(),
                assigneeUserId = old[Todos.assigneeId]?.toString(),
                assigneeEmail = emailOf(old[Todos.assigneeId]),
            )
        }

    fun delete(userId: UUID, listId: UUID, todoId: UUID): Unit = transaction {
        Members.requireMember(userId, listId)
        requireTodo(listId, todoId)
        Todos.deleteWhere { Todos.id eq todoId }
    }

    fun reorder(userId: UUID, listId: UUID, todoId: UUID, req: ReorderTodoRequest): TodoDto =
        transaction {
            Members.requireMember(userId, listId)
            val old = requireTodo(listId, todoId)

            // Current ordered Todos (excluding the one being moved).
            val others = Todos.selectAll().where { Todos.listId eq listId and (Todos.id neq todoId) }
                .orderBy(Todos.orderKey to org.jetbrains.exposed.sql.SortOrder.ASC)
                .map { it[Todos.id] to it[Todos.orderKey] }

            val newKey = if (req.beforeId == null) {
                // Move to the end.
                val key = (others.lastOrNull()?.second ?: 0.0) + 1.0
                Todos.update({ Todos.id eq todoId }) { it[Todos.orderKey] = key }
                key
            } else {
                val targetId = runCatching { UUID.fromString(req.beforeId) }.getOrNull()
                    ?: throw DomainException.badRequest("Invalid beforeId.")
                val targetIndex = others.indexOfFirst { it.first == targetId }
                if (targetIndex < 0) throw DomainException.notFound("Target Todo not found.")
                val prev = if (targetIndex > 0) others[targetIndex - 1].second else null
                val next = others[targetIndex].second
                val candidate = if (prev == null) next - 1.0 else (prev + next) / 2.0
                // The fractional midpoint can exhaust double precision after many
                // inserts into the same gap and collide with a neighbour. When it
                // does, renumber the whole List with integer keys and place the
                // moved Todo before the target — a rare O(n) fallback.
                if (prev != null && (candidate <= prev || candidate >= next)) {
                    rebalance(others, todoId, targetIndex)
                } else {
                    Todos.update({ Todos.id eq todoId }) { it[Todos.orderKey] = candidate }
                    candidate
                }
            }

            old.toDto(orderOverride = newKey, assigneeEmail = emailOf(old[Todos.assigneeId]))
        }

    /**
     * Reassign sequential integer order keys to the whole List with [movedId]
     * inserted at [insertBefore], and return [movedId]'s new key. Used only when
     * the fractional midpoint runs out of precision.
     */
    private fun rebalance(
        others: List<Pair<UUID, Double>>,
        movedId: UUID,
        insertBefore: Int,
    ): Double {
        val ordered = others.map { it.first }.toMutableList()
        ordered.add(insertBefore, movedId)
        var movedKey = 0.0
        ordered.forEachIndexed { index, id ->
            val key = (index + 1).toDouble()
            if (id == movedId) movedKey = key
            Todos.update({ Todos.id eq id }) { it[Todos.orderKey] = key }
        }
        return movedKey
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

    /**
     * Parse a client-supplied due date. Blank means "clear it"; a valid
     * yyyy-MM-dd sets it; anything else is a client error rather than a silent
     * discard that would wipe an existing date.
     */
    private fun parseDueDate(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
            ?: throw DomainException.badRequest("Invalid due date; expected format yyyy-MM-dd.")
    }

    /** The email for a single user id, or null. */
    private fun emailOf(userId: UUID?): String? =
        userId?.let { Users.selectAll().where { Users.id eq it }.firstOrNull()?.get(Users.email) }

    /** Emails for a set of user ids in one query (avoids N+1 on the list endpoint). */
    private fun emailsFor(ids: Set<UUID>): Map<UUID, String> =
        if (ids.isEmpty()) emptyMap()
        else Users.select(Users.id, Users.email).where { Users.id inList ids }
            .associate { it[Users.id] to it[Users.email] }

    private fun ResultRow.toDto(orderOverride: Double? = null, assigneeEmail: String? = null) = TodoDto(
        id = this[Todos.id].toString(),
        listId = this[Todos.listId].toString(),
        title = this[Todos.title],
        description = this[Todos.description],
        dueDate = this[Todos.dueDate]?.toString(),
        completed = this[Todos.completed],
        order = orderOverride ?: this[Todos.orderKey],
        createdAt = this[Todos.createdAt].toString(),
        assigneeUserId = this[Todos.assigneeId]?.toString(),
        assigneeEmail = assigneeEmail,
    )
}
