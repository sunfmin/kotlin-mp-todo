package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateInviteLinkRequest
import com.example.todo.common.CreateListRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.InviteLinkDto
import com.example.todo.common.InvitePreviewDto
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import com.example.todo.common.RenameListRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.Role
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
 * Seam 1 for sharing & membership (slice 5): drives the real HTTP endpoints
 * against Testcontainers Postgres, covering the Invite Link lifecycle (generate,
 * regenerate-revokes-prior, expiry, revoke), join-as-Editor, role-based
 * authorization (owner vs editor vs non-member), the member list, remove, and leave.
 */
class MembershipApiTest {

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

    private suspend fun HttpClient.generateLink(token: String, listId: String, expiresAt: String? = null): InviteLinkDto =
        post(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateInviteLinkRequest(expiresAt))
        }.body()

    private suspend fun HttpClient.join(token: String, inviteToken: String) =
        post(ApiRoutes.inviteJoin(inviteToken)) {
            header(HttpHeaders.Authorization, "Bearer $token")
        }

    @Test
    fun `owner generates a link and a signed-in user joins as editor`() = testApp { client ->
        val owner = client.signIn("owner-join@example.com").accessToken
        val listId = client.createList(owner, "Shared")
        val link = client.generateLink(owner, listId)

        val joinerAuth = client.signIn("joiner@example.com")
        val joined = client.join(joinerAuth.accessToken, link.token)
        assertEquals(HttpStatusCode.OK, joined.status)
        val dto = joined.body<ListDto>()
        assertEquals(listId, dto.id)
        assertEquals(Role.EDITOR, dto.role)

        // The joined list now appears in the joiner's index as an EDITOR.
        val joinerLists = client.get(ApiRoutes.LISTS) {
            header(HttpHeaders.Authorization, "Bearer ${joinerAuth.accessToken}")
        }.body<List<ListDto>>()
        assertEquals(Role.EDITOR, joinerLists.single { it.id == listId }.role)

        // And the editor can add a Todo to the shared list.
        assertEquals(HttpStatusCode.Created, client.post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${joinerAuth.accessToken}")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest("Editor's todo"))
        }.status)
    }

    @Test
    fun `regenerating a link revokes the previous one`() = testApp { client ->
        val owner = client.signIn("regen@example.com").accessToken
        val listId = client.createList(owner, "Regen")
        val first = client.generateLink(owner, listId)
        val second = client.generateLink(owner, listId)
        assertTrue(first.token != second.token)

        val joiner = client.signIn("regen-joiner@example.com").accessToken
        // The prior link no longer works; the new one does.
        assertEquals(HttpStatusCode.NotFound, client.join(joiner, first.token).status)
        assertEquals(HttpStatusCode.OK, client.join(joiner, second.token).status)

        // The owner's current link is the second one.
        val current = client.get(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer $owner")
        }.body<InviteLinkDto>()
        assertEquals(second.token, current.token)
    }

    @Test
    fun `an expired link cannot be used and revoke disables the link`() = testApp { client ->
        val owner = client.signIn("expiry@example.com").accessToken
        val listId = client.createList(owner, "Expiry")
        val joiner = client.signIn("expiry-joiner@example.com").accessToken

        val expired = client.generateLink(owner, listId, expiresAt = "2000-01-01T00:00:00Z")
        assertEquals(HttpStatusCode.NotFound, client.join(joiner, expired.token).status)
        // An expired link is not reported as the active link either.
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer $owner")
        }.status)

        val live = client.generateLink(owner, listId, expiresAt = "2999-01-01T00:00:00Z")
        client.delete(ApiRoutes.inviteLink(listId)) { header(HttpHeaders.Authorization, "Bearer $owner") }
        assertEquals(HttpStatusCode.NotFound, client.join(joiner, live.token).status)
    }

    @Test
    fun `joining requires authentication and preview shows the list name`() = testApp { client ->
        val owner = client.signIn("preview@example.com").accessToken
        val listId = client.createList(owner, "Preview list")
        val link = client.generateLink(owner, listId)

        // Unauthenticated follow of a link is rejected (client prompts sign-in first).
        assertEquals(HttpStatusCode.Unauthorized, client.post(ApiRoutes.inviteJoin(link.token)).status)

        val joiner = client.signIn("preview-joiner@example.com").accessToken
        val preview = client.get(ApiRoutes.invitePreview(link.token)) {
            header(HttpHeaders.Authorization, "Bearer $joiner")
        }.body<InvitePreviewDto>()
        assertEquals("Preview list", preview.listName)
    }

    @Test
    fun `members list shows owner and editors with roles`() = testApp { client ->
        val owner = client.signIn("members-owner@example.com").accessToken
        val listId = client.createList(owner, "Team")
        val link = client.generateLink(owner, listId)
        val editor = client.signIn("members-editor@example.com")
        client.join(editor.accessToken, link.token)

        val members = client.get(ApiRoutes.members(listId)) {
            header(HttpHeaders.Authorization, "Bearer $owner")
        }.body<List<MemberDto>>()
        assertEquals(Role.OWNER, members.first().role)
        assertEquals("members-owner@example.com", members.first().email)
        val editorMember = members.single { it.role == Role.EDITOR }
        assertEquals("members-editor@example.com", editorMember.email)
        assertEquals(editor.userId, editorMember.userId)
    }

    @Test
    fun `only the owner can manage the link and membership`() = testApp { client ->
        val owner = client.signIn("authz-owner@example.com").accessToken
        val stranger = client.signIn("authz-stranger@example.com").accessToken
        val listId = client.createList(owner, "Locked")
        val link = client.generateLink(owner, listId)
        val editor = client.signIn("authz-editor@example.com")
        client.join(editor.accessToken, link.token)

        // Editor cannot manage the invite link, rename, or delete the list (403).
        assertEquals(HttpStatusCode.Forbidden, client.post(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
            contentType(ContentType.Application.Json); setBody(CreateInviteLinkRequest())
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put(ApiRoutes.list(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
            contentType(ContentType.Application.Json); setBody(RenameListRequest("Hijack"))
        }.status)
        assertEquals(HttpStatusCode.Forbidden, client.delete(ApiRoutes.list(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
        }.status)

        // A non-member sees a 404 (existence not leaked).
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.members(listId)) {
            header(HttpHeaders.Authorization, "Bearer $stranger")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.inviteLink(listId)) {
            header(HttpHeaders.Authorization, "Bearer $stranger")
        }.status)
    }

    @Test
    fun `owner removes an editor and an editor can leave`() = testApp { client ->
        val owner = client.signIn("remove-owner@example.com").accessToken
        val listId = client.createList(owner, "Churn")
        val link = client.generateLink(owner, listId)

        val removed = client.signIn("removed@example.com")
        client.join(removed.accessToken, link.token)
        // Owner removes the editor → they lose access.
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.member(listId, removed.userId)) {
            header(HttpHeaders.Authorization, "Bearer $owner")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.list(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${removed.accessToken}")
        }.status)

        val leaver = client.signIn("leaver@example.com")
        client.join(leaver.accessToken, link.token)
        // Editor leaves themselves → loses access.
        assertEquals(HttpStatusCode.NoContent, client.delete(ApiRoutes.member(listId, leaver.userId)) {
            header(HttpHeaders.Authorization, "Bearer ${leaver.accessToken}")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.get(ApiRoutes.list(listId)) {
            header(HttpHeaders.Authorization, "Bearer ${leaver.accessToken}")
        }.status)
    }

    @Test
    fun `owner cannot leave their own list and cannot be removed`() = testApp { client ->
        val ownerAuth = client.signIn("owner-leave@example.com")
        val listId = client.createList(ownerAuth.accessToken, "Mine")

        // Owner self-removal (leave) is rejected — transfer is slice 7.
        assertEquals(HttpStatusCode.Conflict, client.delete(ApiRoutes.member(listId, ownerAuth.userId)) {
            header(HttpHeaders.Authorization, "Bearer ${ownerAuth.accessToken}")
        }.status)

        // An editor cannot remove the owner.
        val link = client.generateLink(ownerAuth.accessToken, listId)
        val editor = client.signIn("owner-leave-editor@example.com")
        client.join(editor.accessToken, link.token)
        assertEquals(HttpStatusCode.Forbidden, client.delete(ApiRoutes.member(listId, ownerAuth.userId)) {
            header(HttpHeaders.Authorization, "Bearer ${editor.accessToken}")
        }.status)
    }

    @Test
    fun `joining twice is idempotent`() = testApp { client ->
        val owner = client.signIn("idem-owner@example.com").accessToken
        val listId = client.createList(owner, "Idem")
        val link = client.generateLink(owner, listId)
        val joiner = client.signIn("idem-joiner@example.com")
        client.join(joiner.accessToken, link.token)
        assertEquals(HttpStatusCode.OK, client.join(joiner.accessToken, link.token).status)

        // Still exactly one editor membership (owner + one editor).
        val members = client.get(ApiRoutes.members(listId)) {
            header(HttpHeaders.Authorization, "Bearer $owner")
        }.body<List<MemberDto>>()
        assertEquals(1, members.count { it.role == Role.EDITOR })
        assertNull(members.firstOrNull { it.userId == joiner.userId && it.role == Role.OWNER })
    }
}
