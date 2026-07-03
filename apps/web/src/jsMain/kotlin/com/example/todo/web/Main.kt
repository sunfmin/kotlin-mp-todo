package com.example.todo.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.todo.clientcore.AppContainer
import com.example.todo.clientcore.auth.AuthPhase
import com.example.todo.clientcore.auth.InMemoryTokenStore
import com.example.todo.clientcore.lists.ListsViewModel
import com.example.todo.clientcore.net.ApiClient
import com.example.todo.common.ListDto
import com.example.todo.common.TodoDto
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.coroutines.MainScope
import org.jetbrains.compose.web.attributes.disabled
import org.jetbrains.compose.web.attributes.InputType
import org.jetbrains.compose.web.dom.Button
import org.jetbrains.compose.web.dom.CheckboxInput
import org.jetbrains.compose.web.dom.Div
import org.jetbrains.compose.web.dom.H1
import org.jetbrains.compose.web.dom.H2
import org.jetbrains.compose.web.dom.Li
import org.jetbrains.compose.web.dom.P
import org.jetbrains.compose.web.dom.Span
import org.jetbrains.compose.web.dom.Text
import org.jetbrains.compose.web.dom.TextInput
import org.jetbrains.compose.web.dom.Ul
import org.jetbrains.compose.web.renderComposable

/**
 * Web entry point (ADR-0001): renders the sign-in flow and Lists app with Compose
 * HTML (real DOM), reusing the shared ViewModels and client-core — it does NOT
 * depend on the shared Compose UI widget tree.
 */
fun main() {
    val baseUrl = "http://localhost:8080"
    val http = ApiClient.withJson(HttpClient(Js.create()))
    val container = AppContainer(http, baseUrl, InMemoryTokenStore(), MainScope())

    renderComposable(rootElementId = "root") {
        H1 { Text("Collaborative Todo") }
        val state by container.authViewModel.state.collectAsState()
        when (state.phase) {
            AuthPhase.EMAIL -> EmailStep(state.loading) { container.authViewModel.submitEmail(it) }
            AuthPhase.CODE -> CodeStep(state.email, state.devCode, state.loading,
                onSubmit = { container.authViewModel.submitCode(it) },
                onChangeEmail = { container.authViewModel.changeEmail() })
            AuthPhase.AUTHENTICATED -> ListsApp(container) { container.authViewModel.signOut() }
        }
        if (state.phase != AuthPhase.AUTHENTICATED) {
            state.error?.let { P { Text("⚠ $it") } }
        }
    }
}

@Composable
private fun ListsApp(container: AppContainer, onSignOut: () -> Unit) {
    val viewModel = remember { container.listsViewModel() }
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    var openList by remember { mutableStateOf<ListDto?>(null) }
    val current = openList?.let { o -> state.lists.firstOrNull { it.id == o.id } ?: o }

    if (current == null) {
        ListsIndex(
            lists = state.lists,
            error = state.error,
            onOpen = { openList = it },
            onCreate = { viewModel.create(it) },
            onRename = { id, name -> viewModel.rename(id, name) },
            onDelete = { viewModel.delete(it) },
            onSignOut = onSignOut,
        )
    } else {
        ListDetail(current, container) { openList = null }
    }
}

@Composable
private fun ListsIndex(
    lists: List<ListDto>,
    error: String?,
    onOpen: (ListDto) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    H2 { Text("Your lists") }
    Button(attrs = { onClick { onSignOut() } }) { Text("Sign out") }
    P {
        TextInput(newName) { onInput { newName = it.value } }
        Button(attrs = {
            if (newName.isBlank()) disabled()
            onClick { onCreate(newName.trim()); newName = "" }
        }) { Text("Add list") }
    }
    error?.let { P { Text("⚠ $it") } }
    if (lists.isEmpty()) {
        P { Text("No lists yet. Create your first one above.") }
    } else {
        Ul {
            lists.forEach { list ->
                Li { ListRow(list, onOpen = { onOpen(list) }, onRename = onRename, onDelete = onDelete) }
            }
        }
    }
}

@Composable
private fun ListRow(
    list: ListDto,
    onOpen: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember(list.id) { mutableStateOf(false) }
    var draft by remember(list.id) { mutableStateOf(list.name) }
    if (editing) {
        TextInput(draft) { onInput { draft = it.value } }
        Button(attrs = { onClick { onRename(list.id, draft.trim()); editing = false } }) { Text("Save") }
        Button(attrs = { onClick { draft = list.name; editing = false } }) { Text("Cancel") }
    } else {
        Button(attrs = { onClick { onOpen() } }) { Text(list.name) }
        Span { Text(" ") }
        Button(attrs = { onClick { editing = true } }) { Text("Rename") }
        Button(attrs = { onClick { onDelete(list.id) } }) { Text("Delete") }
    }
}

@Composable
private fun ListDetail(list: ListDto, container: AppContainer, onBack: () -> Unit) {
    val viewModel = remember(list.id) { container.listDetailViewModel(list.id) }
    LaunchedEffect(list.id) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    Button(attrs = { onClick { onBack() } }) { Text("← Lists") }
    H2 { Text(list.name) }

    // Add todo
    var newTitle by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf("") }
    P {
        TextInput(newTitle) { onInput { newTitle = it.value } }
        Span { Text(" ") }
        Button(attrs = {
            if (newTitle.isBlank()) disabled()
            onClick {
                viewModel.add(
                    newTitle.trim(),
                    newDesc.trim().takeIf(String::isNotEmpty),
                    newDue.trim().takeIf(String::isNotEmpty),
                )
                newTitle = ""; newDesc = ""; newDue = ""
            }
        }) { Text("Add") }
    }
    P {
        TextInput(newDesc) { onInput { newDesc = it.value } }
        Span { Text(" ") }
        TextInput(newDue) { onInput { newDue = it.value } }
    }

    state.error?.let { P { Text("⚠ $it") } }

    if (state.todos.isEmpty()) {
        P { Text("No todos yet. Add your first one above.") }
    } else {
        Ul {
            state.todos.forEachIndexed { idx, todo ->
                Li {
                    WebTodoRow(
                        todo = todo,
                        onToggle = { viewModel.toggle(todo) },
                        onSave = { title, desc, due -> viewModel.update(todo, title, desc, due) },
                        onDelete = { viewModel.delete(todo) },
                        onMoveUp = {
                            if (idx > 0) viewModel.reorder(todo.id, state.todos[idx - 1].id)
                        },
                        onMoveDown = {
                            if (idx < state.todos.size - 1)
                                viewModel.reorder(todo.id, state.todos.getOrNull(idx + 2)?.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun WebTodoRow(
    todo: TodoDto,
    onToggle: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    var editing by remember(todo.id) { mutableStateOf(false) }
    var draftTitle by remember(todo.id) { mutableStateOf(todo.title) }
    var draftDesc by remember(todo.id) { mutableStateOf(todo.description ?: "") }
    var draftDue by remember(todo.id) { mutableStateOf(todo.dueDate ?: "") }

    if (editing) {
        Div {
            TextInput(draftTitle) { onInput { draftTitle = it.value } }
            TextInput(draftDesc) { onInput { draftDesc = it.value } }
            TextInput(draftDue) { onInput { draftDue = it.value } }
            Button(attrs = {
                onClick {
                    // Send blank (not null) for emptied fields so the server clears
                    // them; null would mean "leave unchanged".
                    onSave(draftTitle.trim(), draftDesc.trim(), draftDue.trim())
                    editing = false
                }
            }) { Text("Save") }
            Button(attrs = {
                onClick { draftTitle = todo.title; draftDesc = todo.description ?: ""; draftDue = todo.dueDate ?: ""; editing = false }
            }) { Text("Cancel") }
        }
    } else {
        Div {
            CheckboxInput(checked = todo.completed, attrs = { onClick { onToggle() } })
            Span {
                Text(
                    (if (todo.completed) "✓ " else "") + todo.title +
                        (todo.description?.let { " — $it" } ?: "") +
                        (todo.dueDate?.let { " (due $it)" } ?: ""),
                )
            }
            Span { Text(" ") }
            Button(attrs = { onClick { editing = true } }) { Text("Edit") }
            Button(attrs = { onClick { onMoveUp() } }) { Text("↑") }
            Button(attrs = { onClick { onMoveDown() } }) { Text("↓") }
            Button(attrs = { onClick { onDelete() } }) { Text("Delete") }
        }
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
