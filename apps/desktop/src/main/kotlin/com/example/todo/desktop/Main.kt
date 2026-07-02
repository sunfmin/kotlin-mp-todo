package com.example.todo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.todo.clientcore.health.HealthViewModel
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.uicompose.App
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main() = application {
    val baseUrl = System.getenv("TODO_SERVER_URL") ?: "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(CIO.create()))
    val viewModel = HealthViewModel(ApiClient(http, baseUrl), CoroutineScope(Dispatchers.Default))
    Window(onCloseRequest = ::exitApplication, title = "Collaborative Todo") {
        App(viewModel)
    }
}
