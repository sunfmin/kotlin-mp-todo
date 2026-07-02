package com.example.todo.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.todo.clientcore.auth.AuthViewModel
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.clientcore.net.AuthApi
import com.example.todo.uicompose.App
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main() = application {
    val baseUrl = System.getenv("TODO_SERVER_URL") ?: "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(CIO.create()))
    val api = AuthApi(http, baseUrl, InMemoryTokenStore())
    val viewModel = AuthViewModel(api, CoroutineScope(Dispatchers.Default))
    Window(onCloseRequest = ::exitApplication, title = "Collaborative Todo") {
        App(viewModel)
    }
}
