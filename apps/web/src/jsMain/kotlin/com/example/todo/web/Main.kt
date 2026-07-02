package com.example.todo.web

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.todo.clientcore.health.HealthUiState
import com.example.todo.clientcore.health.HealthViewModel
import com.example.todo.clientcore.net.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.renderComposable

/**
 * Web entry point (ADR-0001): the Web client renders with Compose HTML (real DOM),
 * reusing the shared [HealthViewModel] and client-core — it does NOT depend on the
 * shared Compose UI widget tree.
 */
fun main() {
    val baseUrl = "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(Js.create()))
    val viewModel = HealthViewModel(ApiClient(http, baseUrl), MainScope())

    renderComposable(rootElementId = "root") {
        LaunchedEffect(Unit) { viewModel.load() }
        val state by viewModel.state.collectAsState()

        H1 { Text("Collaborative Todo") }
        when (val s = state) {
            is HealthUiState.Loading -> P { Text("Connecting…") }
            is HealthUiState.Connected ->
                P { Text("Connected to ${s.service} — database ${if (s.databaseConnected) "up" else "down"}") }
            is HealthUiState.Failed -> P { Text("Cannot reach the server: ${s.message}") }
        }
    }
}
