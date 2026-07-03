package com.example.todo.server.realtime

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.UUID

/**
 * In-process fan-out of "List X changed" notifications (slice 8, ADR-0006).
 * Domain services call [published] after a mutation; the SSE endpoint collects
 * [events] and forwards each change to the subscribers who are currently members
 * of that List. The payload is only the List id — clients refetch via REST, so
 * this is a pure notification channel, never a data or write path.
 */
class ChangeNotifier {
    private val _events = MutableSharedFlow<UUID>(extraBufferCapacity = 256)
    val events: SharedFlow<UUID> = _events.asSharedFlow()

    /** Announce that the List with [listId] changed. Non-blocking, best-effort. */
    fun published(listId: UUID) {
        _events.tryEmit(listId)
    }
}
