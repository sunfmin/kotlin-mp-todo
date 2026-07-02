package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateListRequest
import com.example.todo.common.ListDto
import com.example.todo.common.RenameListRequest
import com.example.todo.common.Role
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
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
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for Lists (slice 3): drives the real HTTP endpoints against a
 * Testcontainers Postgres, covering create/list/rename/delete and the
 * ownership + authentication authorization checks.
 */
class ListApiTest {

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

    /** Signs a fresh user in and returns their bearer access token. */
    private suspend fun HttpClient.signIn(email: String): String {
        val code = post(ApiRoutes.AUTH_OTP_REQUEST) {
            contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
        }.body<RequestOtpResponse>().devCode!!
        return post(ApiRoutes.AUTH_OTP_VERIFY) {
            contentType(ContentType.Application.Json); setBody(VerifyOtpRequest(email, code))
        }.body<TokenResponse>().accessToken
    }

    private suspend fun HttpClient.createList(token: String, name: String) =
        post(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateListRequest(name))
        }

    @Test
    fun `create records the caller as owner and appears in their list`() = testApp { client ->
        val token = client.signIn("owner@example.com")

        val created = client.createList(token, "Groceries")
        assertEquals(HttpStatusCode.Created, created.status)
        val dto = created.body<ListDto>()
        assertEquals("Groceries", dto.name)
        assertEquals(Role.OWNER, dto.role)

        val mine = client.get(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.body<List<ListDto>>()
        assertEquals(listOf("Groceries"), mine.map { it.name })
    }

    @Test
    fun `a user sees only their own lists`() = testApp { client ->
        val alice = client.signIn("alice@example.com")
        val bob = client.signIn("bob@example.com")
        client.createList(alice, "Alice list")
        client.createList(bob, "Bob list")

        val aliceLists = client.get(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer $alice")
        }.body<List<ListDto>>()
        assertEquals(listOf("Alice list"), aliceLists.map { it.name })
    }

    @Test
    fun `owner can rename and delete their list`() = testApp { client ->
        val token = client.signIn("rename@example.com")
        val id = client.createList(token, "Old").body<ListDto>().id

        val renamed = client.put(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(RenameListRequest("New"))
        }
        assertEquals(HttpStatusCode.OK, renamed.status)
        assertEquals("New", renamed.body<ListDto>().name)

        val deleted = client.delete(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NoContent, deleted.status)

        val gone = client.get(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.NotFound, gone.status)
    }

    @Test
    fun `a user cannot see, rename, or delete another users list`() = testApp { client ->
        val alice = client.signIn("alice2@example.com")
        val bob = client.signIn("bob2@example.com")
        val id = client.createList(alice, "Private").body<ListDto>().id

        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.put(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
            contentType(ContentType.Application.Json); setBody(RenameListRequest("Hijack"))
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $bob")
        }.status)

        // Alice's list is untouched.
        assertEquals("Private", client.get(ApiRoutes.list(id)) {
            header(HttpHeaders.Authorization, "Bearer $alice")
        }.body<ListDto>().name)
    }

    @Test
    fun `list endpoints reject unauthenticated requests`() = testApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.LISTS).status)
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.LISTS) {
            contentType(ContentType.Application.Json); setBody(CreateListRequest("x"))
        }.status)
    }

    @Test
    fun `empty list name is rejected`() = testApp { client ->
        val token = client.signIn("blank@example.com")
        assertEquals(HttpStatusCode.BadRequest, client.createList(token, "   ").status)
    }
}
