package com.example.todo.clientcore

import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.TodosApi
import com.example.todo.clientcore.todos.ListDetailViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Seam 2 for Todos (slice 4): drives [ListDetailViewModel] against a Ktor
 * [MockEngine], asserting the loading/loaded/error states and that mutations
 * refetch the Todo list.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ListDetailViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun viewModel(scope: TestScope, engine: MockEngine): ListDetailViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return ListDetailViewModel("list-1", TodosApi(authorized), scope)
    }

    @Test
    fun `load surfaces the lists todos`() = runTest {
        val engine = MockEngine {
            respond(
                """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"2026-07-02T00:00:00Z"}]""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(listOf("Milk"), state.todos.map { it.title })
    }

    @Test
    fun `add posts then refetches the updated list`() = runTest {
        var added = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Post &&
                    request.url.encodedPath.endsWith("/todos") -> {
                    added = true
                    respond("""{"id":"t2","listId":"list-1","title":"Bread","completed":false,"order":2.0,"createdAt":"t"}""",
                        HttpStatusCode.Created, jsonHeaders)
                }
                else -> respond(
                    if (added)
                        """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"},
                          {"id":"t2","listId":"list-1","title":"Bread","completed":false,"order":2.0,"createdAt":"t"}]"""
                    else "[]",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)

        vm.add("Bread").join()

        assertEquals(listOf("Milk", "Bread"), vm.state.value.todos.map { it.title })
    }

    @Test
    fun `toggle sends completed true then refetches`() = runTest {
        var toggled = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Put &&
                    request.url.encodedPath.contains("/todos/") &&
                    !request.url.encodedPath.endsWith("/reorder") -> {
                    toggled = true
                    respond("""{"id":"t1","listId":"list-1","title":"Milk","completed":true,"order":1.0,"createdAt":"t"}""",
                        HttpStatusCode.OK, jsonHeaders)
                }
                else -> respond(
                    if (toggled)
                        """[{"id":"t1","listId":"list-1","title":"Milk","completed":true,"order":1.0,"createdAt":"t"}]"""
                    else """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)
        vm.load().join()

        val todo = vm.state.value.todos.first()
        vm.toggle(todo).join()

        assertTrue(vm.state.value.todos.first().completed)
    }

    @Test
    fun `delete removes the todo then refetches`() = runTest {
        var deleted = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Delete &&
                    request.url.encodedPath.contains("/todos/") -> {
                    deleted = true
                    respond("", HttpStatusCode.NoContent, headersOf())
                }
                else -> respond(
                    if (deleted) "[]"
                    else """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)
        vm.load().join()

        vm.delete(vm.state.value.todos.first()).join()

        assertTrue(vm.state.value.todos.isEmpty())
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
