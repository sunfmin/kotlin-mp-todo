package com.example.todo.clientcore

import com.example.todo.clientcore.health.HealthUiState
import com.example.todo.clientcore.health.HealthViewModel
import com.example.todo.clientcore.net.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Seam 2 — a client-core ViewModel driven against a Ktor [MockEngine] with canned
 * JSON, asserting the observable UI state a real screen would render. Validates all
 * four clients at once (they share client-core) with no platform UI launched. This
 * is the canonical client-side test shape later slices extend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HealthViewModelTest {

    private fun viewModel(scope: TestScope, engine: MockEngine): HealthViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        return HealthViewModel(ApiClient(http, "http://test.local"), scope)
    }

    @Test
    fun `successful health response yields Connected state`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"status":"ok","service":"kotlin-mp-todo-server","databaseConnected":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        val state = vm.state.value
        assertIs<HealthUiState.Connected>(state)
        assertEquals("kotlin-mp-todo-server", state.service)
        assertEquals(true, state.databaseConnected)
    }

    @Test
    fun `server error yields Failed state`() = runTest {
        val engine = MockEngine {
            respond(content = "boom", status = HttpStatusCode.InternalServerError)
        }
        val vm = viewModel(this, engine)

        vm.load().join()

        assertIs<HealthUiState.Failed>(vm.state.value)
    }
}
