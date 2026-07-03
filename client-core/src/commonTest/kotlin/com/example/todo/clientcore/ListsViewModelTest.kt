package com.example.todo.clientcore

import com.example.todo.clientcore.lists.ListsViewModel
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.ListsApi
import com.example.todo.clientcore.net.MembershipApi
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.common.ApiRoutes
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

/**
 * Seam 2 for Lists (slice 3): drives [ListsViewModel] against a Ktor [MockEngine],
 * asserting the loading/loaded/error states and that mutations refetch the index.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun viewModel(scope: TestScope, engine: MockEngine): ListsViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return ListsViewModel(ListsApi(authorized), MembershipApi(authorized), scope)
    }

    @Test
    fun `load surfaces the users lists`() = runTest {
        val engine = MockEngine {
            respond(
                """[{"id":"1","name":"Groceries","role":"OWNER","createdAt":"2026-07-02T00:00:00Z"}]""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        val state = vm.state.value
        assertEquals(false, state.loading)
        assertEquals(listOf("Groceries"), state.lists.map { it.name })
    }

    @Test
    fun `create posts then refetches the updated index`() = runTest {
        var created = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath == ApiRoutes.LISTS -> {
                    created = true
                    respond("""{"id":"2","name":"Work","role":"OWNER","createdAt":"t"}""",
                        HttpStatusCode.Created, jsonHeaders)
                }
                else -> respond(
                    if (created)
                        """[{"id":"2","name":"Work","role":"OWNER","createdAt":"t"}]"""
                    else "[]",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)

        vm.create("Work").join()

        assertEquals(listOf("Work"), vm.state.value.lists.map { it.name })
    }

    @Test
    fun `join follows a link then refetches the index with the joined list`() = runTest {
        var joined = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/join") -> {
                    joined = true
                    respond("""{"id":"9","name":"Shared","role":"EDITOR","createdAt":"t"}""",
                        HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond(
                    if (joined) """[{"id":"9","name":"Shared","role":"EDITOR","createdAt":"t"}]"""
                    else "[]",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)

        vm.join("tok-abc").join()

        val shared = vm.state.value.lists.single()
        assertEquals("Shared", shared.name)
        assertEquals("EDITOR", shared.role)
    }

    @Test
    fun `a failed load records an error`() = runTest {
        val engine = MockEngine {
            respond("""{"message":"boom"}""", HttpStatusCode.InternalServerError, jsonHeaders)
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        assertNotNull(vm.state.value.error)
    }
}
