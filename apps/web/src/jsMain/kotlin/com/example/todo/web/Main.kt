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
import com.example.todo.common.MemberDto
import com.example.todo.common.Role
import com.example.todo.common.TodoDto
import com.example.todo.common.inviteTokenOf
import io.ktor.client.HttpClient
import io.ktor.client.engine.js.Js
import kotlinx.browser.window
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
    LaunchedEffect(Unit) {
        viewModel.load()
        // Follow an invite link the user opened (…?invite=<token>) once, then clear it.
        inviteTokenFromUrl()?.let { token ->
            viewModel.join(token)
            window.history.replaceState(null, "", window.location.pathname)
        }
    }
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
            onJoin = { viewModel.join(inviteTokenOf(it)) },
            onSignOut = onSignOut,
        )
    } else {
        ListDetail(current, container) { openList = null }
    }
}

/** The invite token from the page URL's `invite` query parameter, if present. */
private fun inviteTokenFromUrl(): String? {
    val search = window.location.search.removePrefix("?")
    if (search.isEmpty()) return null
    return search.split("&")
        .map { it.split("=", limit = 2) }
        .firstOrNull { it.first() == "invite" }
        ?.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?.let { inviteTokenOf(it) }
}

@Composable
private fun ListsIndex(
    lists: List<ListDto>,
    error: String?,
    onOpen: (ListDto) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onJoin: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var invite by remember { mutableStateOf("") }
    H2 { Text("Your lists") }
    Button(attrs = { onClick { onSignOut() } }) { Text("Sign out") }
    P {
        TextInput(newName) { onInput { newName = it.value } }
        Button(attrs = {
            if (newName.isBlank()) disabled()
            onClick { onCreate(newName.trim()); newName = "" }
        }) { Text("Add list") }
    }
    P {
        TextInput(invite) { onInput { invite = it.value } }
        Button(attrs = {
            if (invite.isBlank()) disabled()
            onClick { onJoin(invite.trim()); invite = "" }
        }) { Text("Join with invite") }
    }
    error?.let { P { Text("⚠ $it") } }
    if (lists.isEmpty()) {
        P { Text("No lists yet. Create your first one above.") }
    } else {
        Ul {
            lists.forEach { list ->
                Li {
                    ListRow(list, onOpen = { onOpen(list) }, onRename = onRename, onDelete = onDelete)
                    Span { Text(" [${if (list.role == Role.OWNER) "owner" else "editor"}]") }
                }
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

    var showMembers by remember(list.id) { mutableStateOf(false) }

    Button(attrs = { onClick { onBack() } }) { Text("← Lists") }
    H2 { Text(list.name) }
    Button(attrs = { onClick { showMembers = !showMembers } }) {
        Text(if (showMembers) "Hide members" else if (list.role == Role.OWNER) "Share & members" else "Members")
    }
    if (showMembers) {
        WebMembers(list, container)
    }

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
private fun WebMembers(list: ListDto, container: AppContainer) {
    val isOwner = list.role == Role.OWNER
    val vm = remember(list.id) { container.membersViewModel(list.id, isOwner) }
    LaunchedEffect(list.id) { vm.load() }
    val state by vm.state.collectAsState()
    val currentEmail = remember { container.currentEmail() }

    Div {
        if (isOwner) {
            val link = state.inviteLink
            if (link == null) {
                P {
                    Text("No active invite link. ")
                    Button(attrs = { onClick { vm.generateInviteLink() } }) { Text("Generate link") }
                }
            } else {
                P { Text("Invite code: ${link.token}") }
                link.expiresAt?.let { P { Text("Expires: $it") } }
                P {
                    Button(attrs = { onClick { vm.generateInviteLink() } }) { Text("Regenerate") }
                    Span { Text(" ") }
                    Button(attrs = { onClick { vm.revokeInviteLink() } }) { Text("Revoke") }
                }
            }
        }

        state.error?.let { P { Text("⚠ $it") } }

        Ul {
            state.members.forEach { member ->
                Li { WebMemberRow(member, isOwner, member.email == currentEmail) { vm.removeMember(member.userId) } }
            }
        }

        if (!isOwner) {
            val me = state.members.firstOrNull { it.email == currentEmail }
            if (me != null) {
                Button(attrs = { onClick { vm.removeMember(me.userId) } }) { Text("Leave this list") }
            }
        }
    }
}

@Composable
private fun WebMemberRow(member: MemberDto, viewerIsOwner: Boolean, isMe: Boolean, onRemove: () -> Unit) {
    val roleLabel = if (member.role == Role.OWNER) "owner" else "editor"
    Span { Text("${member.email} ($roleLabel)${if (isMe) " (you)" else ""}") }
    if (viewerIsOwner && member.role != Role.OWNER) {
        Span { Text(" ") }
        Button(attrs = { onClick { onRemove() } }) { Text("Remove") }
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
