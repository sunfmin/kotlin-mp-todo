package com.example.todo.server

import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.Role
import com.example.todo.server.db.Memberships
import com.example.todo.server.db.Users
import com.example.todo.server.lists.ListService
import com.example.todo.server.membership.Members
import com.example.todo.server.membership.MembershipService
import com.example.todo.server.plugins.DatabaseFactory
import com.example.todo.server.realtime.ChangeNotifier
import com.example.todo.server.todos.TodoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.Collections
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for real-time scoping (slice 8, ADR-0006), verified deterministically at
 * the service level: a mutation publishes a "changed" signal to the
 * [ChangeNotifier], and the exact predicate the SSE route uses to scope delivery
 * (membership) flips off when a member is removed — the teardown-on-revocation rule.
 */
class ChangeNotifierScopingTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply { start() }
        private var connected = false
    }

    private fun connect() {
        if (!connected) {
            DatabaseFactory.connect(postgres.jdbcUrl, postgres.username, postgres.password)
            connected = true
        }
    }

    private fun newUser(email: String): UUID {
        val id = UUID.randomUUID()
        transaction {
            Users.insert {
                it[Users.id] = id
                it[Users.email] = email
                it[Users.createdAt] = Clock.System.now()
            }
        }
        return id
    }

    @Test
    fun `a todo mutation publishes its list id to the notifier`() = runBlocking {
        connect()
        val notifier = ChangeNotifier()
        val lists = ListService(notify = notifier::published)
        val todos = TodoService(notify = notifier::published)

        val ownerId = newUser("notify-owner-${UUID.randomUUID()}@example.com")
        val list = lists.create(ownerId, "L")
        val listId = UUID.fromString(list.id)

        val received = Collections.synchronizedList(mutableListOf<UUID>())
        val job = launch(Dispatchers.Default) { notifier.events.collect { received.add(it) } }
        delay(200) // let the collector subscribe

        todos.create(ownerId, listId, CreateTodoRequest("Task"))
        delay(300)
        job.cancel()

        assertContains(received, listId)
    }

    @Test
    fun `membership scoping flips off when a member is removed`() {
        connect()
        val notifier = ChangeNotifier()
        val lists = ListService(notify = notifier::published)
        val membership = MembershipService(notify = notifier::published)

        val ownerId = newUser("scope-owner-${UUID.randomUUID()}@example.com")
        val editorId = newUser("scope-editor-${UUID.randomUUID()}@example.com")
        val listId = UUID.fromString(lists.create(ownerId, "Shared").id)
        transaction {
            Memberships.insert {
                it[Memberships.listId] = listId
                it[Memberships.userId] = editorId
                it[Memberships.createdAt] = Clock.System.now()
            }
        }

        // While a member, the stream would deliver this List's changes.
        assertNotNull(transaction { Members.roleOf(editorId, listId) })
        assertEquals(Role.EDITOR, transaction { Members.roleOf(editorId, listId) })

        // After removal, the scoping predicate is null → no more delivery (teardown).
        membership.removeMember(ownerId, listId, editorId)
        assertNull(transaction { Members.roleOf(editorId, listId) })
    }
}
