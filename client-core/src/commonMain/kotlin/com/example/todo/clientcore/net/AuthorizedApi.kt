package com.example.todo.clientcore.net

import com.example.todo.clientcore.auth.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType

/**
 * Sends authenticated requests to the server: attaches the stored bearer access
 * token and, on a 401, silently refreshes once and retries (ADR-0003). Every
 * domain API (lists, todos, membership, …) is built on this so the silent-refresh
 * behaviour lives in exactly one place.
 */
class AuthorizedApi(
    private val http: HttpClient,
    baseUrl: String,
    private val store: TokenStore,
    /** Obtains a fresh token pair; typically [AuthApi.refresh]. */
    private val refresh: suspend () -> Boolean,
) {
    private val base = baseUrl.trimEnd('/')

    suspend fun get(path: String): HttpResponse = send(HttpMethod.Get, path)
    suspend fun delete(path: String): HttpResponse = send(HttpMethod.Delete, path)
    suspend fun post(path: String, body: Any? = null): HttpResponse = send(HttpMethod.Post, path, body)
    suspend fun put(path: String, body: Any? = null): HttpResponse = send(HttpMethod.Put, path, body)
    suspend fun patch(path: String, body: Any? = null): HttpResponse = send(HttpMethod.Patch, path, body)

    /** The absolute URL for [path], exposed for transports (e.g. SSE) that bypass [send]. */
    fun url(path: String) = base + path

    /** The current bearer token, if any (for transports that set their own headers). */
    fun accessToken(): String? = store.load()?.accessToken

    private suspend fun send(method: HttpMethod, path: String, body: Any? = null): HttpResponse {
        val first = http.request(url(path)) { configure(method, body) }
        if (first.status.value != 401) return first.ensureOk()
        if (!refresh()) throw ApiException(401, "Session expired")
        return http.request(url(path)) { configure(method, body) }.ensureOk()
    }

    private fun HttpRequestBuilder.configure(httpMethod: HttpMethod, body: Any?) {
        method = httpMethod
        store.load()?.accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        if (body != null) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}
