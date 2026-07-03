package com.example.todo.server

import com.example.todo.common.AccountDeletionInfo
import com.example.todo.common.ApiRoutes
import com.example.todo.common.AssignTodoRequest
import com.example.todo.common.CreateInviteLinkRequest
import com.example.todo.common.CreateListRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import com.example.todo.common.Role
import com.example.todo.common.TodoDto
import com.example.todo.common.TransferOwnershipRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for the membership lifecycle invariants (slice 7, ADR-0009):
 * auto-unassign on remove/leave, ownership transfer (and blocked owner-leave),
 * and blocked/allowed account deletion.
 */
class MembershipLifecycleApiTest {

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

    private suspend fun HttpClient.shareWith(ownerToken: String, listId: String, joiner: TokenResponse) {
        val link = post(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer $ownerToken")
            contentType(ContentType.Application.Json); setBody(CreateInviteLinkRequest())
        }.body<InviteLinkDto>()
        post(ApiRoutes.inviteJoin(link.token)) { header(HttpHeaders.Authorization, "Bearer ${joiner.accessToken}") }
    }

    private suspend fun HttpClient.todos(token: String, listId: String): List<TodoDto> =
        get(ApiRoutes.todos(listId)) { header(HttpHeaders.Authorization, "Bearer $token") }.body()

    @Test
    fun `removing a member auto-unassigns their todos`() = testApp { client ->
        val owner = client.signIn("lc-owner@example.com")
        val listId = client.createList(owner.accessToken, "Chores")
        val editor = client.signIn("lc-editor@example.com")
        client.shareWith(owner.accessToken, listId, editor)

        val todoId = client.createTodo(owner.accessToken, listId, "Dishes")
        client.put(ApiRoutes.todoAssignee(listId, todoId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json); setBody(AssignTodoRequest(editor.userId))
        }

        // Remove the editor → their todo is auto-unassigned, not blocked.
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.member(listId, editor.userId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.status)
        assertNull(client.todos(owner.accessToken, listId).single().assigneeUserId)
    }

    @Test
    fun `ownership transfer swaps roles and keeps exactly one owner`() = testApp { client ->
        val owner = client.signIn("xfer-owner@example.com")
        val listId = client.createList(owner.accessToken, "Shared")
        val editor = client.signIn("xfer-editor@example.com")
        client.shareWith(owner.accessToken, listId, editor)

        val afterTransfer = client.post(ApiRoutes.listTransfer(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json); setBody(TransferOwnershipRequest(editor.userId))
        }.body<ListDto>()
        assertEquals(Role.EDITOR, afterTransfer.role) // caller is now an editor

        val members = client.get(ApiRoutes.members(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
        }.body<List<MemberDto>>()
        assertEquals(1, members.count { it.role == Role.OWNER })
        assertEquals(editor.userId, members.single { it.role == Role.OWNER }.userId)
        assertEquals(owner.userId, members.single { it.role == Role.EDITOR }.userId)

        // The former owner is now an editor and may leave.
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.member(listId, owner.userId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.status)
    }

    @Test
    fun `transfer requires an owner caller and a member target`() = testApp { client ->
        val owner = client.signIn("xfer2-owner@example.com")
        val listId = client.createList(owner.accessToken, "Shared2")
        val editor = client.signIn("xfer2-editor@example.com")
        client.shareWith(owner.accessToken, listId, editor)
        val stranger = client.signIn("xfer2-stranger@example.com")

        // Editor cannot transfer.
        assertEquals(HttpStatusCode.Forbidden, client.post(ApiRoutes.listTransfer(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
            contentType(ContentType.Application.Json); setBody(TransferOwnershipRequest(owner.userId))
        }.status)
        // Cannot transfer to a non-member.
        assertEquals(HttpStatusCode.BadRequest, client.post(ApiRoutes.listTransfer(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json); setBody(TransferOwnershipRequest(stranger.userId))
        }.status)
    }

    @Test
    fun `account deletion is blocked while owning a shared list and names it`() = testApp { client ->
        val owner = client.signIn("del-owner@example.com")
        val listId = client.createList(owner.accessToken, "Family")
        val editor = client.signIn("del-editor@example.com")
        client.shareWith(owner.accessToken, listId, editor)

        val resp = client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }
        assertEquals(HttpStatusCode.Conflict, resp.status)
        val info = resp.body<AccountDeletionInfo>()
        assertEquals(listOf("Family"), info.blockingLists.map { it.name })

        // The blockers pre-check endpoint agrees.
        val blockers = client.get(ApiRoutes.ACCOUNT_DELETION_BLOCKERS) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.body<AccountDeletionInfo>()
        assertEquals(listId, blockers.blockingLists.single().id)
    }

    @Test
    fun `account deletion succeeds for solo-owned lists and removes the data`() = testApp { client ->
        val user = client.signIn("solo@example.com")
        client.createList(user.accessToken, "Just me")

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${user.accessToken}")
        }.status)

        // Signing in again with the same email is a fresh account with no lists.
        val reborn = client.signIn("solo@example.com")
        assertTrue(client.get(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer ${reborn.accessToken}")
        }.body<List<ListDto>>().isEmpty())
    }

    @Test
    fun `deleting a member account unassigns their todos elsewhere and ends membership`() = testApp { client ->
        val owner = client.signIn("host@example.com")
        val listId = client.createList(owner.accessToken, "Hosted")
        val guest = client.signIn("guest@example.com")
        client.shareWith(owner.accessToken, listId, guest)
        val todoId = client.createTodo(owner.accessToken, listId, "Guest task")
        client.put(ApiRoutes.todoAssignee(listId, todoId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
            contentType(ContentType.Application.Json); setBody(AssignTodoRequest(guest.userId))
        }

        // Guest owns no shared list, so they can delete their account.
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${guest.accessToken}")
        }.status)

        // Their assignment is cleared and their membership is gone.
        assertNull(client.todos(owner.accessToken, listId).single().assigneeUserId)
        val members = client.get(ApiRoutes.members(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${owner.accessToken}")
        }.body<List<MemberDto>>()
        assertEquals(listOf(Role.OWNER), members.map { it.role })
    }
}
