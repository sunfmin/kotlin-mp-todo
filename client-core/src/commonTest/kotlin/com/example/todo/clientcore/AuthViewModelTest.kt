package com.example.todo.clientcore

import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.AuthViewModel
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthApi
import com.example.todo.common.ApiRoutes
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
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
 * Seam 2 for OTP auth: drives [AuthViewModel] against a Ktor [MockEngine], asserting
 * the observable UI state through the email -> code -> authenticated flow and the
 * wrong-code error path. Covers all four clients (shared client-core).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun viewModel(scope: TestScope, engine: MockEngine): AuthViewModel {
        val http = ApiClient.withJson(HttpClient(engine))
        return AuthViewModel(AuthApi(http, "http://test.local", InMemoryTokenStore()), scope)
    }

    @Test
    fun `submitting email moves to the code step and surfaces the dev code`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.AUTH_OTP_REQUEST -> respond(
                    """{"devCode":"123456"}""", HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val vm = viewModel(this, engine)

        vm.submitEmail("alex@example.com").join()

        val state = vm.state.value
        assertEquals(AuthPhase.CODE, state.phase)
        assertEquals("alex@example.com", state.email)
        assertEquals("123456", state.devCode)
        assertNull(state.error)
    }

    @Test
    fun `entering the correct code authenticates`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.AUTH_OTP_REQUEST -> respond("""{"devCode":"123456"}""", HttpStatusCode.OK, jsonHeaders)
                ApiRoutes.AUTH_OTP_VERIFY -> respond(
                    """{"accessToken":"a","refreshToken":"r","accessTokenExpiresInSeconds":900,"userId":"u","email":"alex@example.com"}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val vm = viewModel(this, engine)

        vm.submitEmail("alex@example.com").join()
        vm.submitCode("123456").join()

        val state = vm.state.value
        assertEquals(AuthPhase.AUTHENTICATED, state.phase)
        assertEquals("alex@example.com", state.email)
    }

    @Test
    fun `a wrong code keeps the code step and shows an error`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.encodedPath) {
                ApiRoutes.AUTH_OTP_REQUEST -> respond("""{"devCode":"123456"}""", HttpStatusCode.OK, jsonHeaders)
                ApiRoutes.AUTH_OTP_VERIFY -> respond(
                    """{"message":"Incorrect code."}""", HttpStatusCode.Unauthorized, jsonHeaders,
                )
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        val vm = viewModel(this, engine)

        vm.submitEmail("alex@example.com").join()
        vm.submitCode("000000").join()

        val state = vm.state.value
        assertEquals(AuthPhase.CODE, state.phase)
        assertNotNull(state.error)
        assertEquals("Incorrect code.", state.error)
    }
}
