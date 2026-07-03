package com.example.todo.clientcore.net

import com.example.todo.common.ApiRoutes
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.sse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A source of "List X changed" notifications (slice 8, ADR-0006). Each emitted
 * value is a changed List id; the caller refetches that List via REST. The flow
 * completes or throws when the connection drops, so the caller can reconnect.
 */
interface ListChangeStream {
    fun listChanges(): Flow<String>
}

/**
 * Server-Sent Events implementation of [ListChangeStream]. An EventSource cannot
 * set an Authorization header, so the access token rides as a `token` query param
 * (the server verifies it). If SSE proves unreliable on a target this is the one
 * seam to swap for a push-only WebSocket (ADR-0006) — nothing else changes.
 */
class SseChangeStream(
    private val http: HttpClient,
    private val authorized: AuthorizedApi,
) : ListChangeStream {

    override fun listChanges(): Flow<String> = flow {
        val token = authorized.accessToken() ?: return@flow
        val url = authorized.url(ApiRoutes.EVENTS) + "?token=$token"
        http.sse(url) {
            incoming.collect { event ->
                if (event.event == ApiRoutes.EVENT_LIST_CHANGED) {
                    event.data?.let { emit(it) }
                }
            }
        }
    }
}
