package com.example.todo.clientcore

import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.ListChangeStream
import com.example.todo.clientcore.net.MembershipApi
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
        return ListDetailViewModel("list-1", TodosApi(authorized), MembershipApi(authorized), scope)
    }

    private fun viewModel(scope: TestScope, engine: MockEngine, changes: ListChangeStream): ListDetailViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return ListDetailViewModel("list-1", TodosApi(authorized), MembershipApi(authorized), scope, changes)
    }

    /** A controllable [ListChangeStream]: [push] queues ids; [failFirst] drops the first connection. */
    private class FakeChanges(private val failFirst: Boolean = false) : ListChangeStream {
        private val channel = Channel<String>(Channel.UNLIMITED)
        private var calls = 0
        override fun listChanges(): Flow<String> = flow {
            calls++
            if (failFirst && calls == 1) throw RuntimeException("connection dropped")
            channel.consumeAsFlow().collect { emit(it) }
        }
        fun push(id: String) { channel.trySend(id) }
    }

    private fun growingTodosEngine(): MockEngine {
        var fetches = 0
        return MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> {
                    fetches++
                    respond(
                        if (fetches >= 2)
                            """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"},
                                {"id":"t2","listId":"list-1","title":"Bread","completed":false,"order":2.0,"createdAt":"t"}]"""
                        else """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"}]""",
                        HttpStatusCode.OK, jsonHeaders,
                    )
                }
            }
        }
    }

    @Test
    fun `load surfaces the lists todos`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> respond(
                    """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"2026-07-02T00:00:00Z"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
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
                request.url.encodedPath.endsWith("/members") ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
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
                request.url.encodedPath.endsWith("/members") ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
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
    fun `load surfaces members for the assignee picker`() = runTest {
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/members") -> respond(
                    """[{"userId":"u1","email":"owner@x.com","role":"OWNER"},
                       {"userId":"u2","email":"ed@x.com","role":"EDITOR"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("[]", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        assertEquals(listOf("owner@x.com", "ed@x.com"), vm.state.value.members.map { it.email })
    }

    @Test
    fun `assign puts the assignee then refetches the todos`() = runTest {
        var assigned = false
        val engine = MockEngine { request ->
            when {
                request.method == HttpMethod.Put && request.url.encodedPath.endsWith("/assignee") -> {
                    assigned = true
                    respond("""{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t","assigneeUserId":"u2","assigneeEmail":"ed@x.com"}""",
                        HttpStatusCode.OK, jsonHeaders)
                }
                request.url.encodedPath.endsWith("/members") ->
                    respond("[]", HttpStatusCode.OK, jsonHeaders)
                else -> respond(
                    if (assigned)
                        """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t","assigneeUserId":"u2","assigneeEmail":"ed@x.com"}]"""
                    else """[{"id":"t1","listId":"list-1","title":"Milk","completed":false,"order":1.0,"createdAt":"t"}]""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val vm = viewModel(this, engine)
        vm.load().join()

        vm.assign(vm.state.value.todos.first(), "u2").join()

        assertEquals("ed@x.com", vm.state.value.todos.first().assigneeEmail)
    }

    // The real-time tests use runBlocking (real time) rather than the virtual-time
    // runTest, because the notification-driven refetch runs through the Ktor
    // MockEngine on its own dispatcher and is awaited by observing the state.
    private fun realtimeVm(scope: kotlinx.coroutines.CoroutineScope, engine: MockEngine, changes: ListChangeStream): ListDetailViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return ListDetailViewModel("list-1", TodosApi(authorized), MembershipApi(authorized), scope, changes)
    }

    @Test
    fun `a change notification triggers a refetch`() = runBlocking {
        val changes = FakeChanges()
        val vm = realtimeVm(this, growingTodosEngine(), changes)
        vm.load().join()
        assertEquals(1, vm.state.value.todos.size)

        val job = launch { vm.observeChanges() }
        changes.push("list-1")
        withTimeout(3_000) { vm.state.first { it.todos.size == 2 } }
        job.cancel()

        assertEquals(listOf("Milk", "Bread"), vm.state.value.todos.map { it.title })
    }

    @Test
    fun `the subscription reconnects after a dropped connection`() = runBlocking {
        val changes = FakeChanges(failFirst = true)
        val vm = realtimeVm(this, growingTodosEngine(), changes)
        vm.load().join()

        val job = launch { vm.observeChanges() }
        changes.push("list-1") // buffered; delivered after the reconnect
        // First connect drops, backoff (~2s) elapses, then the reconnected stream refetches.
        withTimeout(6_000) { vm.state.first { it.todos.size == 2 } }
        job.cancel()

        assertEquals(2, vm.state.value.todos.size)
    }

    @Test
    fun `a change for a different list is ignored`() = runBlocking {
        val changes = FakeChanges()
        val vm = realtimeVm(this, growingTodosEngine(), changes)
        vm.load().join()

        val job = launch { vm.observeChanges() }
        changes.push("some-other-list")
        delay(300) // give the (ignored) event time to be processed
        job.cancel()

        assertEquals(1, vm.state.value.todos.size) // unchanged
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
