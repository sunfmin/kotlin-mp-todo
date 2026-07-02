package com.example.todo.clientcore.auth

import com.example.todo.clientcore.net.AuthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which step of sign-in the UI shows. */
enum class AuthPhase { EMAIL, CODE, AUTHENTICATED }

data class AuthState(
    val phase: AuthPhase = AuthPhase.EMAIL,
    val email: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    /** Dev convenience: the emailed code, surfaced when the server runs in dev mode. */
    val devCode: String? = null,
)

/**
 * Drives passwordless sign-in (ADR-0003): email -> code -> authenticated. Shared by
 * all four clients (ADR-0001). load()-style methods return the launched [Job] so tests
 * can await them deterministically.
 */
class AuthViewModel(
    private val api: AuthApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    /** On startup, promote to authenticated if a stored session is still valid. */
    fun restoreSession(): Job = scope.launch {
        _state.value = _state.value.copy(loading = true)
        _state.value = try {
            val me = api.me()
            AuthState(phase = AuthPhase.AUTHENTICATED, email = me.email)
        } catch (_: Exception) {
            AuthState(phase = AuthPhase.EMAIL)
        }
    }

    fun submitEmail(email: String): Job = scope.launch {
        _state.value = _state.value.copy(email = email, loading = true, error = null)
        _state.value = try {
            val resp = api.requestOtp(email)
            _state.value.copy(phase = AuthPhase.CODE, loading = false, devCode = resp.devCode)
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not send the code")
        }
    }

    fun submitCode(code: String): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        _state.value = try {
            val tokens = api.verifyOtp(_state.value.email, code)
            AuthState(phase = AuthPhase.AUTHENTICATED, email = tokens.email)
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not verify the code")
        }
    }

    /** Back to the email step (e.g. wrong address). */
    fun changeEmail() {
        _state.value = AuthState(phase = AuthPhase.EMAIL)
    }

    fun signOut(): Job = scope.launch {
        api.signOut()
        _state.value = AuthState(phase = AuthPhase.EMAIL)
    }
}
