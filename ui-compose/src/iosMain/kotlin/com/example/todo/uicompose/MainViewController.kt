package com.example.todo.uicompose

import androidx.compose.ui.window.ComposeUIViewController
import com.example.todo.clientcore.AppContainer
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.net.ApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import platform.UIKit.UIViewController

/**
 * iOS entry point (ADR-0001): the Xcode app links the `UiCompose` framework and
 * presents this view controller. Builds the shared client stack with the Darwin
 * HTTP engine and renders the shared [App] (sign-in → Lists).
 */
fun MainViewController(baseUrl: String = "http://localhost:8080"): UIViewController {
    val http = ApiClient.withJson(HttpClient(Darwin.create()))
    val container = AppContainer(http, baseUrl, InMemoryTokenStore(), CoroutineScope(Dispatchers.Main))
    return ComposeUIViewController { App(container) }
}
