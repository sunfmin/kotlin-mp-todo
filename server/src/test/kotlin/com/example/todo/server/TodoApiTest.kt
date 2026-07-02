package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateListRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ReorderTodoRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TodoDto
import com.example.todo.common.TokenResponse
import com.example.todo.common.UpdateTodoRequest
import com.example.todo.common.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for Todos (slice 4): drives the real HTTP endpoints against a
 * Testcontainers Postgres, covering CRUD, completion, reorder, and the
 * ownership + authentication authorization checks.
 */
class TodoApiTest {

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

    private suspend fun HttpClient.signIn(email: String): String {
        val code = post(ApiRoutes.AUTH_OTP_REQUEST) {
            contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
        }.body<RequestOtpResponse>().devCode!!
        return post(ApiRoutes.AUTH_OTP_VERIFY) {
            contentType(ContentType.Application.Json); setBody(VerifyOtpRequest(email, code))
        }.body<TokenResponse>().accessToken
    }

    private suspend fun HttpClient.createList(token: String, name: String): String =
        post(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateListRequest(name))
        }.body<com.example.todo.common.ListDto>().id

    private suspend fun HttpClient.createTodo(token: String, listId: String, title: String) =
        post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest(title))
        }

    @Test
    fun `create records a todo in the list and lists it`() = testApp { client ->
        val token = client.signIn("todo-owner@example.com")
        val listId = client.createList(token, "Groceries")

        val created = client.createTodo(token, listId, "Milk")
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<TodoDto>()
        assertEquals("Milk", dto.title)
        assertEquals(listId, dto.listId)
        assertFalse(dto.completed)

        val todos = client.get(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<TodoDto>>()
        assertEquals(listOf("Milk"), todos.map { it.title })
    }

    @Test
    fun `empty title is rejected`() = testApp { client ->
        val token = client.signIn("blank-todo@example.com")
        val listId = client.createList(token, "Work")
        assertEquals(HttpStatusCode.BadRequest, client.createTodo(token, listId, "   ").status)
    }

    @Test
    fun `toggle complete persists`() = testApp { client ->
        val token = client.signIn("toggle@example.com")
        val listId = client.createList(token, "Tasks")
        val id = client.createTodo(token, listId, "A").body<TodoDto>().id

        val toggled = client.put(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(UpdateTodoRequest(completed = true))
        }.body<TodoDto>()
        assertTrue(toggled.completed)

        val fetched = client.get(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<TodoDto>()
        assertTrue(fetched.completed)
    }

    @Test
    fun `edit title description and due date`() = testApp { client ->
        val token = client.signIn("edit@example.com")
        val listId = client.createList(token, "Edit list")
        val id = client.createTodo(token, listId, "Old").body<TodoDto>().id

        val updated = client.put(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(UpdateTodoRequest(title = "New", description = "Details", dueDate = "2026-12-31"))
        }.body<TodoDto>()
        assertEquals("New", updated.title)
        assertEquals("Details", updated.description)
        assertEquals("2026-12-31", updated.dueDate)
    }

    @Test
    fun `delete removes the todo`() = testApp { client ->
        val token = client.signIn("delete@example.com")
        val listId = client.createList(token, "Delete list")
        val id = client.createTodo(token, listId, "Doomed").body<TodoDto>().id

        val deleted = client.delete(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val gone = client.get(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, gone.status)
    }

    @Test
    fun `reorder moves a todo before another and persists order`() = testApp { client ->
        val token = client.signIn("reorder@example.com")
        val listId = client.createList(token, "Order list")
        val a = client.createTodo(token, listId, "A").body<TodoDto>().id
        val b = client.createTodo(token, listId, "B").body<TodoDto>().id
        val c = client.createTodo(token, listId, "C").body<TodoDto>().id

        // Move C before A → expected order: C, A, B
        val reordered = client.patch(ApiRoutes.todoReorder(listId, c)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(ReorderTodoRequest(beforeId = a))
        }.body<TodoDto>()
        assertEquals("C", reordered.title)

        val todos = client.get(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<TodoDto>>()
        assertEquals(listOf("C", "A", "B"), todos.map { it.title })
    }

    @Test
    fun `reorder to end appends`() = testApp { client ->
        val token = client.signIn("reorder-end@example.com")
        val listId = client.createList(token, "End list")
        val a = client.createTodo(token, listId, "A").body<TodoDto>().id
        val b = client.createTodo(token, listId, "B").body<TodoDto>().id

        // Move A to end (beforeId = null) → expected order: B, A
        client.patch(ApiRoutes.todoReorder(listId, a)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(ReorderTodoRequest(beforeId = null))
        }
        val todos = client.get(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<TodoDto>>()
        assertEquals(listOf("B", "A"), todos.map { it.title })
    }

    @Test
    fun `a user cannot see or mutate another users todos`() = testApp { client ->
        val alice = client.signIn("alice-todo@example.com")
        val bob = client.signIn("bob-todo@example.com")
        val listId = client.createList(alice, "Alice's list")
        val id = client.createTodo(alice, listId, "Secret").body<TodoDto>().id

        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest("Hack"))
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.put(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
            contentType(ContentType.Application.Json); setBody(UpdateTodoRequest(title = "Hijack"))
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
        }.status)

        // Alice's todo is untouched.
        assertEquals("Secret", client.get(ApiRoutes.todo(listId, id)) {
            header(HttpHeaders.Authorization, "Bearer $alice")
        }.body<TodoDto>().title)
    }

    @Test
    fun `todo endpoints reject unauthenticated requests`() = testApp { client ->
        val token = client.signIn("auth-check@example.com")
        val listId = client.createList(token, "Auth list")

        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.todos(listId)).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.todos(listId)) {
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest("x"))
        }.status)
    }

    @Test
    fun `a todo cannot be created in a list the user does not own`() = testApp { client ->
        val alice = client.signIn("alice-orphan@example.com")
        val bob = client.signIn("bob-orphan@example.com")
        val listId = client.createList(alice, "Private")

        assertEquals(HttpStatusCode.NotFound, client.post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest("Sneaky"))
        }.status)
    }
}
