package com.example.todo.clientcore

import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.clientcore.membership.MembersViewModel
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.MembershipApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Seam 2 for sharing & membership (slice 5): drives [MembersViewModel] against a
 * Ktor [MockEngine], asserting the members + Invite Link states and that owner
 * actions refetch. Owner-vs-editor authorization itself is a server concern
 * (covered by seam 1); here [isOwner] only gates the link fetch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MembersViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun viewModel(scope: TestScope, isOwner: Boolean, engine: MockEngine): MembersViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return MembersViewModel("list-1", isOwner, MembershipApi(authorized), scope)
    }

    @Test
    fun `owner load surfaces members and the active invite link`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") -> respond(
                    """[{"userId":"u1","email":"owner@x.com","role":"OWNER"},
                       {"userId":"u2","email":"ed@x.com","role":"EDITOR"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") -> respond(
                    """{"token":"tok-123","expiresAt":null}""", HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.load().join()

        val state = vm.state.value
        assertEquals(listOf("owner@x.com", "ed@x.com"), state.members.map { it.email })
        assertEquals("tok-123", state.inviteLink?.token)
    }

    @Test
    fun `editor load does not fetch the invite link`() = runTest {
        var linkFetched = false
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") -> respond(
                    """[{"userId":"u1","email":"owner@x.com","role":"OWNER"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") -> {
                    linkFetched = true
                    respond("""{"token":"nope"}""", HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = false, engine)

        vm.load().join()

        assertEquals(false, linkFetched)
        assertNull(vm.state.value.inviteLink)
    }

    @Test
    fun `no active link is surfaced as null, not an error`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") -> respond(
                    """[{"userId":"u1","email":"owner@x.com","role":"OWNER"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") ->
                    respond("""{"message":"No active invite link."}""", HttpStatusCode.NotFound, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.load().join()

        assertNull(vm.state.value.inviteLink)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `generate link posts then refetches with the new link`() = runTest {
        var generated = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/invite-link") -> {
                    generated = true
                    respond("""{"token":"new-tok","expiresAt":null}""", HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath.endsWith("/members") -> respond(
                    """[{"userId":"u1","email":"owner@x.com","role":"OWNER"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") -> respond(
                    if (generated) """{"token":"new-tok","expiresAt":null}"""
                    else """{"message":"none"}""",
                    if (generated) HttpStatusCode.OK else HttpStatusCode.NotFound, jsonHeaders,
                )
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.generateInviteLink().join()

        assertEquals("new-tok", vm.state.value.inviteLink?.token)
    }

    @Test
    fun `remove member deletes then refetches the member list`() = runTest {
        var removed = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Delete && request.url.encodedPath.contains("/members/") -> {
                    removed = true
                    respond("", HttpStatusCode.NoContent, headersOf())
                }
                request.url.encodedPath.endsWith("/members") -> respond(
                    if (removed) """[{"userId":"u1","email":"owner@x.com","role":"OWNER"}]"""
                    else """[{"userId":"u1","email":"owner@x.com","role":"OWNER"},
                             {"userId":"u2","email":"ed@x.com","role":"EDITOR"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") ->
                    respond("""{"message":"none"}""", HttpStatusCode.NotFound, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.removeMember("u2").join()

        assertEquals(listOf("owner@x.com"), vm.state.value.members.map { it.email })
    }

    @Test
    fun `transfer ownership posts then refetches the members`() = runTest {
        var transferred = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/transfer") -> {
                    transferred = true
                    respond("""{"id":"list-1","name":"L","role":"EDITOR","createdAt":"t"}""",
                        HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath.endsWith("/members") -> respond(
                    if (transferred)
                        """[{"userId":"u2","email":"ed@x.com","role":"OWNER"},
                            {"userId":"u1","email":"owner@x.com","role":"EDITOR"}]"""
                    else """[{"userId":"u1","email":"owner@x.com","role":"OWNER"},
                            {"userId":"u2","email":"ed@x.com","role":"EDITOR"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                request.url.encodedPath.endsWith("/invite-link") ->
                    respond("""{"message":"none"}""", HttpStatusCode.NotFound, jsonHeaders)
                else -> respond("", HttpStatusCode.NotFound, headersOf())
            }
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.transferOwnership("u2").join()

        assertEquals("u2", vm.state.value.members.first { it.role == com.example.todo.common.Role.OWNER }.userId)
    }

    @Test
    fun `a failed members load records an error`() = runTest {
        val engine = MockEngine {
            respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        val vm = viewModel(this, isOwner = true, engine)

        vm.load().join()

        assertNotNull(vm.state.value.error)
    }
}
