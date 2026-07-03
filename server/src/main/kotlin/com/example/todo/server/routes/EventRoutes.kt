package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.server.auth.JwtSupport
import com.example.todo.server.membership.Members
import com.example.todo.server.realtime.ChangeNotifier
import io.ktor.server.routing.Route
import io.ktor.server.sse.sse
import kotlinx.coroutines.flow.collect
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

const val SSE_EVENT_LIST_CHANGED = ApiRoutes.EVENT_LIST_CHANGED

/**
 * The change-notification stream (slice 8, ADR-0006). An EventSource cannot set
 * an Authorization header, so the access token is passed as a `token` query param
 * and verified here. The stream is scoped per event to Lists the User is a member
 * of *at that moment*, so once their membership is revoked they stop receiving a
 * List's changes without tearing the whole stream down.
 */
fun Route.eventRoutes(notifier: ChangeNotifier, jwt: JwtSupport) {
    sse(ApiRoutes.EVENTS) {
        val token = call.request.queryParameters["token"]
        val userId = token
            ?.let { runCatching { jwt.verifier.verify(it) }.getOrNull() }
            ?.subject
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (userId == null) {
            close()
            return@sse
        }
        notifier.events.collect { listId ->
            val isMember = transaction { Members.roleOf(userId, listId) != null }
            if (isMember) {
                send(data = listId.toString(), event = SSE_EVENT_LIST_CHANGED)
            }
        }
    }
}
