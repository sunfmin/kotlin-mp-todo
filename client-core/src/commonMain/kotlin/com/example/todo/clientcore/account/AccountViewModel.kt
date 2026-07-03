package com.example.todo.clientcore.account

import com.example.todo.clientcore.net.AccountApi
import com.example.todo.common.ListDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing state for the account-deletion flow (slice 7, seam 2). */
data class AccountState(
    val loading: Boolean = false,
    /** Shared Lists that must be transferred or deleted before the account can go. */
    val blockingLists: List<ListDto> = emptyList(),
    val deleted: Boolean = false,
    val error: String? = null,
)

/**
 * Drives the account-deletion pre-check + deletion shared by all four clients.
 * The server is authoritative (ADR-0002); this only surfaces the blocking Lists
 * and performs the delete, invoking [onDeleted] so the shell can sign out.
 */
class AccountViewModel(
    private val api: AccountApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AccountState())
    val state: StateFlow<AccountState> = _state.asStateFlow()

    /** Load the shared Lists that currently block deletion. */
    fun loadBlockers(): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        _state.value = try {
            AccountState(loading = false, blockingLists = api.deletionBlockers())
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not check your account")
        }
    }

    /** Attempt deletion; on success invokes [onDeleted]. Re-surfaces blockers on failure. */
    fun deleteAccount(onDeleted: () -> Unit): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        try {
            api.deleteAccount()
            _state.value = _state.value.copy(loading = false, deleted = true)
            onDeleted()
        } catch (e: Exception) {
            // Re-check so the UI shows exactly which Lists still block deletion.
            val blockers = runCatching { api.deletionBlockers() }.getOrDefault(emptyList())
            _state.value = _state.value.copy(
                loading = false,
                blockingLists = blockers,
                error = e.message ?: "Could not delete your account",
            )
        }
    }
}
