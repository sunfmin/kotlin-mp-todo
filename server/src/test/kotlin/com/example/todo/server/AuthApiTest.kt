package com.example.todo.server

import com.example.todo.common.MeResponse
import com.example.todo.common.RefreshRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 for OTP auth (ADR-0003): drives the real HTTP endpoints against a
 * Testcontainers Postgres. The test app enables `auth.otpReturnInResponse` so the
 * flow can read the code it would otherwise receive by email.
 */
class AuthApiTest {

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

    private suspend fun HttpClient.requestCode(email: String): String {
        val resp = post(com.example.todo.common.ApiRoutes.AUTH_OTP_REQUEST) {
            contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
        }
        assertEquals(HttpStatusCode.OK, resp.status)
        return assertNotNull(resp.body<RequestOtpResponse>().devCode, "dev code should be present in test mode")
    }

    private suspend fun HttpClient.verify(email: String, code: String) =
        post(com.example.todo.common.ApiRoutes.AUTH_OTP_VERIFY) {
            contentType(ContentType.Application.Json); setBody(VerifyOtpRequest(email, code))
        }

    @Test
    fun `full sign-in flow issues tokens and authorizes protected endpoint`() = testApp { client ->
        val email = "alex@example.com"
        val code = client.requestCode(email)

        val verifyResp = client.verify(email, code)
        assertEquals(HttpStatusCode.OK, verifyResp.status)
        val tokens = verifyResp.body<TokenResponse>()
        assertEquals(email, tokens.email)

        val me = client.get(com.example.todo.common.ApiRoutes.ME) {
            header(HttpHeaders.Authorization, "Bearer ${tokens.accessToken}")
        }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(email, me.body<MeResponse>().email)
    }

    @Test
    fun `wrong code is rejected`() = testApp { client ->
        val email = "wrong@example.com"
        val code = client.requestCode(email)
        val wrong = "%06d".format((code.toInt() + 1) % 1_000_000)

        assertEquals(HttpStatusCode.Unauthorized, client.verify(email, wrong).status)
    }

    @Test
    fun `protected endpoint requires a token`() = testApp { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(com.example.todo.common.ApiRoutes.ME).status)
    }

    @Test
    fun `refresh rotates tokens and invalidates the old one`() = testApp { client ->
        val email = "refresh@example.com"
        val tokens = client.verify(email, client.requestCode(email)).body<TokenResponse>()

        val refreshed = client.post(com.example.todo.common.ApiRoutes.AUTH_TOKEN_REFRESH) {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.OK, refreshed.status)
        val newTokens = refreshed.body<TokenResponse>()
        assertNotEquals(tokens.refreshToken, newTokens.refreshToken)

        // Old refresh token no longer works after rotation.
        val reuse = client.post(com.example.todo.common.ApiRoutes.AUTH_TOKEN_REFRESH) {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
    }

    @Test
    fun `sign out invalidates the refresh token`() = testApp { client ->
        val email = "signout@example.com"
        val tokens = client.verify(email, client.requestCode(email)).body<TokenResponse>()

        val out = client.post(com.example.todo.common.ApiRoutes.AUTH_SIGNOUT) {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.NoContent, out.status)

        val afterSignout = client.post(com.example.todo.common.ApiRoutes.AUTH_TOKEN_REFRESH) {
            contentType(ContentType.Application.Json); setBody(RefreshRequest(tokens.refreshToken))
        }
        assertEquals(HttpStatusCode.Unauthorized, afterSignout.status)
    }

    @Test
    fun `otp requests are rate limited`() = testApp { client ->
        val email = "flood@example.com"
        repeat(5) {
            val r = client.post(com.example.todo.common.ApiRoutes.AUTH_OTP_REQUEST) {
                contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
            }
            assertEquals(HttpStatusCode.OK, r.status)
        }
        val sixth = client.post(com.example.todo.common.ApiRoutes.AUTH_OTP_REQUEST) {
            contentType(ContentType.Application.Json); setBody(RequestOtpRequest(email))
        }
        assertEquals(HttpStatusCode.TooManyRequests, sixth.status)
    }
}
