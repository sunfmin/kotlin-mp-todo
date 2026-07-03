package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateListRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ListDto
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
import com.example.todo.server.routes.SSE_EVENT_LIST_CHANGED
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.sse.sse
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for real-time updates (slice 8, ADR-0006): the SSE stream delivers
 * "list-changed" notifications only for Lists the authenticated User is a member
 * of, and stops for a List once their membership is revoked.
 */
class EventStreamApiTest {

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
        val client = createClient {
            install(ContentNegotiation) { json() }
            install(SSE)
        }
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

    private suspend fun HttpClient.addTodo(token: String, listId: String, title: String) {
        post(ApiRoutes.todos(listId)) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json); setBody(CreateTodoRequest(title))
        }
    }

    @Test
    fun `an unauthenticated stream receives nothing`() = testApp { client ->
        val owner = client.signIn("evt-noauth@example.com")
        val listId = client.createList(owner.accessToken, "Private")

        val received = mutableListOf<String>()
        coroutineScope {
            val streamJob = launch {
                runCatching {
                    client.sse(ApiRoutes.EVENTS) { // no token → server closes the stream
                        incoming.collect { if (it.event == SSE_EVENT_LIST_CHANGED) it.data?.let(received::add) }
                    }
                }
            }

            // Poke changes for a while; an unauthenticated stream must deliver nothing.
            repeat(6) {
                client.addTodo(owner.accessToken, listId, "z")
                delay(100)
            }
            streamJob.cancel()
        }

        assertTrue(received.isEmpty(), "an unauthenticated stream must not deliver events")
    }
}
