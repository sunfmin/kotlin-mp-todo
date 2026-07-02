package com.example.todo.clientcore.net

import com.example.todo.clientcore.auth.StoredTokens
import com.example.todo.clientcore.auth.TokenStore
import com.example.todo.common.ApiError
import com.example.todo.common.ApiRoutes
import com.example.todo.common.MeResponse
import com.example.todo.common.RefreshRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.RequestOtpResponse
import com.example.todo.common.TokenResponse
import com.example.todo.common.VerifyOtpRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** Raised for non-2xx API responses, carrying the server's message when available. */
class ApiException(val status: Int, override val message: String) : RuntimeException(message)

/**
 * Auth operations against the server (ADR-0003). Persists tokens to the injected
 * [TokenStore] and transparently refreshes the access token on a 401 (silent refresh).
 */
class AuthApi(
    private val http: HttpClient,
    private val baseUrl: String,
    private val store: TokenStore,
) {
    private fun url(path: String) = baseUrl.trimEnd('/') + path

    suspend fun requestOtp(email: String): RequestOtpResponse =
        http.post(url(ApiRoutes.AUTH_OTP_REQUEST)) {
            contentType(ContentType.Application.Json)
            setBody(RequestOtpRequest(email))
        }.ok().body()

    suspend fun verifyOtp(email: String, code: String): TokenResponse {
        val tokens = http.post(url(ApiRoutes.AUTH_OTP_VERIFY)) {
            contentType(ContentType.Application.Json)
            setBody(VerifyOtpRequest(email, code))
        }.ok().body<TokenResponse>()
        store.save(StoredTokens(tokens.accessToken, tokens.refreshToken, tokens.email))
        return tokens
    }

    /** Fetches the current identity, refreshing the access token once on a 401. */
    suspend fun me(): MeResponse {
        val first = requestMe()
        if (first.status.value != 401) return first.ok().body()
        if (!refresh()) throw ApiException(401, "Session expired")
        return requestMe().ok().body()
    }

    /** Uses the stored refresh token to obtain a new token pair. Returns false if it fails. */
    suspend fun refresh(): Boolean {
        val refreshToken = store.load()?.refreshToken ?: return false
        val resp = http.post(url(ApiRoutes.AUTH_TOKEN_REFRESH)) {
            contentType(ContentType.Application.Json)
            setBody(RefreshRequest(refreshToken))
        }
        if (!resp.status.isSuccess()) {
            store.clear()
            return false
        }
        val tokens = resp.body<TokenResponse>()
        store.save(StoredTokens(tokens.accessToken, tokens.refreshToken, tokens.email))
        return true
    }

    suspend fun signOut() {
        val refreshToken = store.load()?.refreshToken
        if (refreshToken != null) {
            runCatching {
                http.post(url(ApiRoutes.AUTH_SIGNOUT)) {
                    contentType(ContentType.Application.Json)
                    setBody(RefreshRequest(refreshToken))
                }
            }
        }
        store.clear()
    }

    private suspend fun requestMe(): HttpResponse =
        http.get(url(ApiRoutes.ME)) {
            store.load()?.accessToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
        }

    private suspend fun HttpResponse.ok(): HttpResponse {
        if (!status.isSuccess()) {
            val message = runCatching { body<ApiError>().message }.getOrNull()
                ?: "Request failed (${status.value})"
            throw ApiException(status.value, message)
        }
        return this
    }
}
