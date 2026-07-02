package com.example.todo.clientcore.health

import com.example.todo.clientcore.net.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing state a client renders for a server request (ADR-0002: online-only). */
sealed interface HealthUiState {
    data object Loading : HealthUiState
    data class Connected(val service: String, val databaseConnected: Boolean) : HealthUiState
    data class Failed(val message: String) : HealthUiState
}

/**
 * Walking-skeleton ViewModel: calls the server health endpoint and exposes the
 * loading/connected/failed state a UI renders. Later slices replace this with the
 * real auth/list/todo ViewModels; the shape (StateFlow of a sealed UI state) is the
 * pattern every client screen follows.
 */
class HealthViewModel(
    private val api: ApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<HealthUiState>(HealthUiState.Loading)
    val state: StateFlow<HealthUiState> = _state.asStateFlow()

    /** Returns the launched [Job] so callers/tests can await completion. */
    fun load(): Job {
        _state.value = HealthUiState.Loading
        return scope.launch {
            _state.value = try {
                val health = api.health()
                HealthUiState.Connected(health.service, health.databaseConnected)
            } catch (e: Exception) {
                HealthUiState.Failed(e.message ?: "Unable to reach the server")
            }
        }
    }
}
