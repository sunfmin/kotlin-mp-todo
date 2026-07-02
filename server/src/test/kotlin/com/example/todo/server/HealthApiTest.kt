package com.example.todo.server

import com.example.todo.common.ApiRoutes
import com.example.todo.common.HealthResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.testcontainers.containers.PostgreSQLContainer

/**
 * Seam 1 — the server HTTP API driven over real HTTP via Ktor `testApplication`,
 * backed by a real Postgres (Testcontainers) so Flyway migrations and Exposed
 * queries actually execute. This is the canonical server integration-test shape
 * that every later slice extends.
 */
class HealthApiTest {

    companion object {
        // Started once on first access; Testcontainers' Ryuk tears it down at JVM exit.
        private val postgres = PostgreSQLContainer("postgres:16-alpine").apply { start() }
    }

    private fun testApp(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        environment {
            config = MapApplicationConfig(
                "db.jdbcUrl" to postgres.jdbcUrl,
                "db.user" to postgres.username,
                "db.password" to postgres.password,
            )
        }
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json() }
        }
        block(client)
    }

    @Test
    fun `health endpoint reports ok and database connected`() = testApp { client ->
        val response = client.get(ApiRoutes.HEALTH)
        assertEquals(HttpStatusCode.OK, response.status)

        val body = response.body<HealthResponse>()
        assertEquals("ok", body.status)
        assertEquals("kotlin-mp-todo-server", body.service)
        assertTrue(body.databaseConnected, "database should be reachable through Testcontainers Postgres")
    }
}
