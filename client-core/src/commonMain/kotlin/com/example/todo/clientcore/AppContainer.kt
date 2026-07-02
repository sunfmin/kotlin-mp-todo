package com.example.todo.clientcore

import com.example.todo.clientcore.auth.AuthViewModel
import com.example.todo.clientcore.auth.TokenStore
import com.example.todo.clientcore.lists.ListsViewModel
import com.example.todo.clientcore.net.AuthApi
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.ListsApi
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope

/**
 * Assembles the shared client stack (ADR-0001/0002) from a platform-provided
 * [HttpClient] engine, base URL, [TokenStore], and coroutine scope — so each of the
 * four entry points wires the app in one line instead of duplicating construction.
 */
class AppContainer(
    http: HttpClient,
    baseUrl: String,
    store: TokenStore,
    val scope: CoroutineScope,
) {
    private val authApi = AuthApi(http, baseUrl, store)
    private val authorized = AuthorizedApi(http, baseUrl, store, refresh = authApi::refresh)

    val authViewModel = AuthViewModel(authApi, scope)

    private val listsApi = ListsApi(authorized)

    /** A fresh Lists index ViewModel (call once per authenticated session). */
    fun listsViewModel() = ListsViewModel(listsApi, scope)
}
