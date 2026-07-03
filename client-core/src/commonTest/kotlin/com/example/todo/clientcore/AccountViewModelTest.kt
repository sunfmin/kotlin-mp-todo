package com.example.todo.clientcore

import com.example.todo.clientcore.account.AccountViewModel
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.clientcore.net.AccountApi
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthorizedApi
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
import kotlin.test.assertTrue

/**
 * Seam 2 for account deletion (slice 7): drives [AccountViewModel] against a Ktor
 * [MockEngine], asserting the blocker pre-check and the delete outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun viewModel(scope: TestScope, engine: MockEngine): AccountViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        val store = InMemoryTokenStore(StoredTokens("access", "refresh", "me@example.com"))
        val authorized = AuthorizedApi(http, "http://test.local", store, refresh = { false })
        return AccountViewModel(AccountApi(authorized), scope)
    }

    @Test
    fun `loadBlockers surfaces the shared lists blocking deletion`() = runTest {
        val engine = MockEngine {
            respond(
                """{"blockingLists":[{"id":"1","name":"Family","role":"OWNER","createdAt":"t"}]}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }
        val vm = viewModel(this, engine)

        vm.loadBlockers().join()

        assertEquals(listOf("Family"), vm.state.value.blockingLists.map { it.name })
    }

    @Test
    fun `deleteAccount success marks deleted and signs out`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Delete) respond("", HttpStatusCode.NoContent, headersOf())
            else respond("""{"blockingLists":[]}""", HttpStatusCode.OK, jsonHeaders)
        }
        val vm = viewModel(this, engine)
        var signedOut = false

        vm.deleteAccount { signedOut = true }.join()

        assertTrue(vm.state.value.deleted)
        assertTrue(signedOut)
    }

    @Test
    fun `deleteAccount blocked re-surfaces the blockers and does not sign out`() = runTest {
        val engine = MockEngine { request ->
            if (request.method == HttpMethod.Delete)
                respond("""{"blockingLists":[{"id":"1","name":"Team","role":"OWNER","createdAt":"t"}]}""",
                    HttpStatusCode.Conflict, jsonHeaders)
            else respond("""{"blockingLists":[{"id":"1","name":"Team","role":"OWNER","createdAt":"t"}]}""",
                HttpStatusCode.OK, jsonHeaders)
        }
        val vm = viewModel(this, engine)
        var signedOut = false

        vm.deleteAccount { signedOut = true }.join()

        assertFalse(vm.state.value.deleted)
        assertFalse(signedOut)
        assertEquals(listOf("Team"), vm.state.value.blockingLists.map { it.name })
    }
}
