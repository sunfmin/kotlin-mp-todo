package com.example.todo.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.AuthViewModel
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.renderComposable

/**
 * Web entry point (ADR-0001): the Web client renders the sign-in flow with Compose
 * HTML (real DOM), reusing the shared [AuthViewModel] and client-core — it does NOT
 * depend on the shared Compose UI widget tree.
 */
fun main() {
    val baseUrl = "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(Js.create()))
    val viewModel = AuthViewModel(AuthApi(http, baseUrl, InMemoryTokenStore()), MainScope())

    renderComposable(rootElementId = "root") {
        H1 { Text("Collaborative Todo") }
        val state by viewModel.state.collectAsState()
        when (state.phase) {
            AuthPhase.EMAIL -> EmailStep(state.loading) { viewModel.submitEmail(it) }
            AuthPhase.CODE -> CodeStep(state.email, state.devCode, state.loading,
                onSubmit = { viewModel.submitCode(it) },
                onChangeEmail = { viewModel.changeEmail() })
            AuthPhase.AUTHENTICATED -> {
                P { Text("Signed in as ${state.email}") }
                P { Text("Your lists will appear here.") }
                Button(attrs = { onClick { viewModel.signOut() } }) { Text("Sign out") }
            }
        }
        state.error?.let { P(attrs = { }) { Text("⚠ $it") } }
    }
}

@Composable
private fun EmailStep(loading: Boolean, onSubmit: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    P { Text("Sign in with your email") }
    TextInput(email) { onInput { email = it.value } }
    Button(attrs = {
        if (loading || email.isBlank()) disabled()
        onClick { onSubmit(email.trim()) }
    }) { Text(if (loading) "…" else "Send code") }
}

@Composable
private fun CodeStep(
    email: String,
    devCode: String?,
    loading: Boolean,
    onSubmit: (String) -> Unit,
    onChangeEmail: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    P { Text("Enter the code sent to $email") }
    devCode?.let { P { Text("Dev code: $it") } }
    TextInput(code) { onInput { code = it.value.filter(Char::isDigit).take(6) } }
    Button(attrs = {
        if (loading || code.length != 6) disabled()
        onClick { onSubmit(code) }
    }) { Text(if (loading) "…" else "Verify") }
    Button(attrs = { onClick { onChangeEmail() } }) { Text("Use a different email") }
}
