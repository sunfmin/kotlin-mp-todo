package com.example.todo.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.todo.clientcore.AppContainer
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.uicompose.App
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 10.0.2.2 is the host loopback as seen from the Android emulator.
        val baseUrl = "http://10.0.2.2:8080"
        val http = ApiClient.withJson(HttpClient(OkHttp.create()))
        val container = AppContainer(http, baseUrl, InMemoryTokenStore(), CoroutineScope(Dispatchers.Main))
        setContent { App(container) }
    }
}
