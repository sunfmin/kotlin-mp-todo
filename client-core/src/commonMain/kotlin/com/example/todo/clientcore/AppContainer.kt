package com.example.todo.clientcore

import com.example.todo.clientcore.auth.AuthViewModel
import com.example.todo.clientcore.auth.TokenStore
import com.example.todo.clientcore.lists.ListsViewModel
import com.example.todo.clientcore.membership.MembersViewModel
import com.example.todo.clientcore.net.AuthApi
import com.example.todo.clientcore.net.AuthorizedApi
import com.example.todo.clientcore.net.ListsApi
import com.example.todo.clientcore.net.MembershipApi
import com.example.todo.clientcore.net.TodosApi
import com.example.todo.clientcore.todos.ListDetailViewModel
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
    private val store: TokenStore,
    val scope: CoroutineScope,
) {
    private val authApi = AuthApi(http, baseUrl, store)
    private val authorized = AuthorizedApi(http, baseUrl, store, refresh = authApi::refresh)

    val authViewModel = AuthViewModel(authApi, scope)

    private val listsApi = ListsApi(authorized)
    private val todosApi = TodosApi(authorized)
    private val membershipApi = MembershipApi(authorized)

    /** A fresh Lists index ViewModel (call once per authenticated session). */
    fun listsViewModel() = ListsViewModel(listsApi, membershipApi, scope)

    /** A fresh List-detail ViewModel for one List (call once per opened List). */
    fun listDetailViewModel(listId: String) = ListDetailViewModel(listId, todosApi, scope)

    /** A fresh members/sharing ViewModel for one List (call once per opened panel). */
    fun membersViewModel(listId: String, isOwner: Boolean) =
        MembersViewModel(listId, isOwner, membershipApi, scope)

    /** The signed-in User's email, used by the UI to find "me" in a member list. */
    fun currentEmail(): String? = store.load()?.email
}
