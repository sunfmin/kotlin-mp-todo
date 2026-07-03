package com.example.todo.clientcore.lists

import com.example.todo.clientcore.net.ListsApi
import com.example.todo.clientcore.net.MembershipApi
import com.example.todo.common.ListDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing state for the List index (ADR-0002: online-only, server-authoritative). */
data class ListsState(
    val loading: Boolean = false,
    val lists: List<ListDto> = emptyList(),
    val error: String? = null,
)

/**
 * Drives the List index screen shared by all four clients (ADR-0001). Every mutation
 * refetches from the server so the client never holds a divergent local copy. Actions
 * return the launched [Job] so tests can await them deterministically.
 */
class ListsViewModel(
    private val api: ListsApi,
    private val membership: MembershipApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ListsState())
    val state: StateFlow<ListsState> = _state.asStateFlow()

    fun load(): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        _state.value = try {
            ListsState(loading = false, lists = api.all())
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not load your lists")
        }
    }

    fun create(name: String): Job = mutateThenReload { api.create(name) }

    fun rename(id: String, name: String): Job = mutateThenReload { api.rename(id, name) }

    fun delete(id: String): Job = mutateThenReload { api.delete(id) }

    /** Follow an Invite Link token to join a shared List, then refresh the index (slice 5). */
    fun join(token: String): Job = mutateThenReload { membership.join(token) }

    private fun mutateThenReload(op: suspend () -> Unit): Job = scope.launch {
        _state.value = _state.value.copy(error = null)
        try {
            op()
            _state.value = _state.value.copy(lists = api.all())
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Something went wrong")
        }
    }
}
