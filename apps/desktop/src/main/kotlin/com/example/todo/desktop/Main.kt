package com.example.todo.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.example.todo.clientcore.AppContainer
import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.lists.ListsViewModel
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.common.ListDto
import com.example.todo.common.inviteTokenOf
import com.example.todo.uicompose.AccountHost
import com.example.todo.uicompose.ListsIndex
import com.example.todo.uicompose.ListDetail
import com.example.todo.uicompose.MembersHost
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

fun main() = application {
    val baseUrl = System.getenv("TODO_SERVER_URL") ?: "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(CIO.create()))
    val container = AppContainer(http, baseUrl, InMemoryTokenStore(), CoroutineScope(Dispatchers.Default))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Collaborative Todo",
        state = rememberWindowState(width = 1100.dp, height = 720.dp),
    ) {
        MaterialTheme { DesktopApp(container) }
    }
}

@Composable
private fun DesktopApp(container: AppContainer) {
    val authState by container.authViewModel.state.collectAsState()

    when (authState.phase) {
        AuthPhase.EMAIL, AuthPhase.CODE -> {
            // Reuse the shared sign-in screen
            com.example.todo.uicompose.App(container)
        }
        AuthPhase.AUTHENTICATED -> {
            MasterDetail(container) { container.authViewModel.signOut() }
        }
    }
}

@Composable
private fun MasterDetail(container: AppContainer, onSignOut: () -> Unit) {
    val listsVm = remember { container.listsViewModel() }
    LaunchedEffect(Unit) { listsVm.load() }
    val listsState by listsVm.state.collectAsState()

    var selectedList by remember { mutableStateOf<ListDto?>(null) }
    var showingAccount by remember { mutableStateOf(false) }
    // Keep selection fresh after renames
    val current = selectedList?.let { s -> listsState.lists.firstOrNull { it.id == s.id } ?: s }

    LaunchedEffect(listsState.lists) {
        if (current == null && listsState.lists.isNotEmpty()) {
            selectedList = listsState.lists.first()
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left sidebar — Lists
        Surface(
            modifier = Modifier.width(280.dp).fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ) {
            ListsIndex(
                lists = listsState.lists,
                error = listsState.error,
                onOpen = { selectedList = it; showingAccount = false },
                onCreate = { listsVm.create(it) },
                onRename = { id, name -> listsVm.rename(id, name) },
                onDelete = { id ->
                    listsVm.delete(id)
                    if (selectedList?.id == id) selectedList = null
                },
                onJoin = { listsVm.join(inviteTokenOf(it)) },
                onManageAccount = { showingAccount = true },
                onSignOut = onSignOut,
                selectedId = if (showingAccount) null else current?.id,
            )
        }

        // Pane divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // Right detail — account screen, or Todos/members for the selected list
        if (showingAccount) {
            Surface(modifier = Modifier.fillMaxSize()) {
                AccountHost(
                    container = container,
                    onBack = { showingAccount = false },
                    onDeleted = onSignOut,
                )
            }
        } else if (current != null) {
            var showingMembers by remember(current.id) { mutableStateOf(false) }
            Surface(modifier = Modifier.fillMaxSize()) {
                if (showingMembers) {
                    MembersHost(
                        container = container,
                        list = current,
                        onBack = { showingMembers = false },
                        onLeft = {
                            showingMembers = false
                            if (selectedList?.id == current.id) selectedList = null
                            listsVm.load()
                        },
                    )
                } else {
                    val detailVm = remember(current.id) { container.listDetailViewModel(current.id) }
                    LaunchedEffect(current.id) { detailVm.load() }
                    LaunchedEffect(current.id) { detailVm.observeChanges() }
                    val detailState by detailVm.state.collectAsState()
                    ListDetail(
                        list = current,
                        state = detailState,
                        onBack = { selectedList = null },
                        onAdd = { t, d, dt -> detailVm.add(t, d, dt) },
                        onToggle = { detailVm.toggle(it) },
                        onSave = { todo, t, d, dt -> detailVm.update(todo, t, d, dt) },
                        onDelete = { detailVm.delete(it) },
                        onReorder = { id, before -> detailVm.reorder(id, before) },
                        onAssign = { todo, assignee -> detailVm.assign(todo, assignee) },
                        onToggleAssignedToMe = { detailVm.setAssignedToMeOnly(it) },
                        currentEmail = remember { container.currentEmail() },
                        onOpenMembers = { showingMembers = true },
                        showBackButton = false,
                    )
                }
            }
        } else {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Select a list to get started",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
