package com.example.todo.clientcore.net

import com.example.todo.common.ApiRoutes
import com.example.todo.common.HealthResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Thin HTTP client over the server API (ADR-0002: no local cache — clients are a
 * UI over the server). Engine-agnostic: each platform app injects its own
 * [HttpClient] engine, keeping this module free of platform dependencies.
 */
class ApiClient(
    private val http: HttpClient,
    /** e.g. "http://localhost:8080" — no trailing slash. */
    private val baseUrl: String,
) {
    suspend fun health(): HealthResponse =
        http.get(baseUrl.trimEnd('/') + ApiRoutes.HEALTH).body()

    companion object {
        /** Adds JSON content negotiation and SSE support to an engine-provided [HttpClient]. */
        fun withJson(http: HttpClient): HttpClient = http.config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(SSE)
        }
    }
}
