package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.AssignTodoRequest
import com.example.todo.common.CreateInviteLinkRequest
import com.example.todo.common.CreateListRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.ListDto
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TodoDto
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for Todo assignment (slice 6): assign/unassign/reassign, the
 * assignee-must-be-a-member rejection, and the assigned-to-me query.
 */
class TodoAssignmentApiTest {

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    private fun testApp(block: suspend (HttpClient) -> Unit) = testApplication {
        environment {
            config = MapApplicationConfig(
                "db.jdbcUrl" to postgres.jdbcUrl,
                "db.user" to postgres.username,
                "db.password" to postgres.password,
                "auth.jwtSecret" to "test-secret",
                "auth.otpReturnInResponse" to "true",
            )
        }
        application { module() }
        val client = createClient { install(ContentNegotiation) { json() } }
        block(client)
    }

    private suspend fun HttpClient.signIn(email: String): TokenResponse {
        val code = post(ApiRoutes.AUTH_OTP_REQUEST) {
            contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
        }.body<RequestOtpResponse>().devCode!!
        return post(ApiRoutes.AUTH_OTP_VERIFY) {
            contentType(ContentType.Application.Json); setBody(VerifyOtpRequest(email, code))
        }.body()
    }

    private suspend fun HttpClient.createList(token: String, name: String): String =
        post(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateListRequest(name))
        }.body<ListDto>().id

    private suspend fun HttpClient.createTodo(token: String, listId: String, title: String): String =
        post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest(title))
        }.body<TodoDto>().id

    private suspend fun HttpClient.assign(token: String, listId: String, todoId: String, assignee: String?) =
        put(ApiRoutes.todoAssignee(listId, todoId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(AssignTodoRequest(assignee))
        }

    /** Owner shares a list and an editor joins; returns (owner, ownerId, listId, editor, editorId). */
    private suspend fun HttpClient.sharedList(name: String): SharedList {
        val owner = signIn("owner-$name@example.com")
        val listId = createList(owner.accessToken, name)
        val link = post(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json); setBody(CreateInviteLinkRequest())
        }.body<InviteLinkDto>()
        val editor = signIn("editor-$name@example.com")
        post(ApiRoutes.inviteJoin(link.token)) { header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}") }
        return SharedList(owner, listId, editor)
    }

    private data class SharedList(val owner: TokenResponse, val listId: String, val editor: TokenResponse)

    @Test
    fun `assign to a member, reassign, then unassign`() = testApp { client ->
        val (owner, listId, editor) = client.sharedList("assign")
        val todoId = client.createTodo(owner.accessToken, listId, "Task")

        val assigned = client.assign(owner.accessToken, listId, todoId, editor.userId).body<TodoDto>()
        assertEquals(editor.userId, assigned.assigneeUserId)
        assertEquals("editor-assign@example.com", assigned.assigneeEmail)

        // Reassign to the owner.
        val reassigned = client.assign(owner.accessToken, listId, todoId, owner.userId).body<TodoDto>()
        assertEquals(owner.userId, reassigned.assigneeUserId)

        // Unassign (null).
        val unassigned = client.assign(owner.accessToken, listId, todoId, null).body<TodoDto>()
        assertNull(unassigned.assigneeUserId)
        assertNull(unassigned.assigneeEmail)
    }

    @Test
    fun `assigning to a non-member is rejected`() = testApp { client ->
        val (owner, listId, _) = client.sharedList("nonmember")
        val stranger = client.signIn("stranger-nm@example.com")
        val todoId = client.createTodo(owner.accessToken, listId, "Task")

        assertEquals(HttpStatusCode.BadRequest, client.assign(owner.accessToken, listId, todoId, stranger.userId).status)

        // The Todo remains unassigned.
        assertNull(client.get(ApiRoutes.todo(listId, todoId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.body<TodoDto>().assigneeUserId)
    }

    @Test
    fun `an editor can assign within a shared list`() = testApp { client ->
        val (owner, listId, editor) = client.sharedList("editorassign")
        val todoId = client.createTodo(owner.accessToken, listId, "Task")

        // The editor assigns the Todo to themselves.
        val assigned = client.assign(editor.accessToken, listId, todoId, editor.userId).body<TodoDto>()
        assertEquals(editor.userId, assigned.assigneeUserId)
    }

    @Test
    fun `assigned-to-me is derivable from the list with assignee ids`() = testApp { client ->
        val (owner, listId, editor) = client.sharedList("mine")
        val mine = client.createTodo(owner.accessToken, listId, "Mine")
        val theirs = client.createTodo(owner.accessToken, listId, "Theirs")
        client.createTodo(owner.accessToken, listId, "Nobody's")
        client.assign(owner.accessToken, listId, mine, editor.userId)
        client.assign(owner.accessToken, listId, theirs, owner.userId)

        val todos = client.get(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
        }.body<List<TodoDto>>()
        val assignedToEditor = todos.filter { it.assigneeUserId == editor.userId }.map { it.title }
        assertEquals(listOf("Mine"), assignedToEditor)
    }
}
