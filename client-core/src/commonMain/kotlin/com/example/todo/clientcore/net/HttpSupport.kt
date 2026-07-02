package com.example.todo.clientcore.net

import com.example.todo.common.ApiError
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess

/** Raised for non-2xx API responses, carrying the server's message when available. */
class ApiException(val status: Int, override val message: String) : RuntimeException(message)

/** Throws [ApiException] (with the server's [ApiError] message when present) on a non-2xx response. */
suspend fun HttpResponse.ensureOk(): HttpResponse {
    if (!status.isSuccess()) {
        val message = runCatching { body<ApiError>().message }.getOrNull()
            ?: "Request failed (${status.value})"
        throw ApiException(status.value, message)
    }
    return this
}
