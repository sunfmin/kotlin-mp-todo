package com.example.todo.uicompose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.AppContainer
import com.example.todo.clientcore.lists.ListsState
import com.example.todo.clientcore.membership.MembersState
import com.example.todo.clientcore.todos.ListDetailState
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import com.example.todo.common.Role
import com.example.todo.common.TodoDto
import com.example.todo.common.inviteTokenOf

/**
 * The authenticated Lists app: index of the user's Lists with create/rename/delete,
 * and a detail view opened by tapping a List.
 */
@Composable
fun ListsApp(container: AppContainer, onSignOut: () -> Unit) {
    val viewModel = remember { container.listsViewModel() }
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    var openList by remember { mutableStateOf<ListDto?>(null) }
    val current = openList?.let { o -> state.lists.firstOrNull { it.id == o.id } ?: o }
    var showingMembers by remember(current?.id) { mutableStateOf(false) }
    var showingAccount by remember { mutableStateOf(false) }

    if (current == null && showingAccount) {
        AccountHost(container, onBack = { showingAccount = false }, onDeleted = onSignOut)
    } else if (current == null) {
        ListsIndex(
            lists = state.lists,
            error = state.error,
            onOpen = { openList = it },
            onCreate = { viewModel.create(it) },
            onRename = { id, name -> viewModel.rename(id, name) },
            onDelete = { id -> viewModel.delete(id) },
            onJoin = { viewModel.join(inviteTokenOf(it)) },
            onManageAccount = { showingAccount = true },
            onSignOut = onSignOut,
        )
    } else if (showingMembers) {
        MembersHost(
            container = container,
            list = current,
            onBack = { showingMembers = false },
            onLeft = { showingMembers = false; openList = null; viewModel.load() },
        )
    } else {
        val detailViewModel = remember(current.id) { container.listDetailViewModel(current.id) }
        LaunchedEffect(current.id) { detailViewModel.load() }
        ListDetail(
            list = current,
            state = detailViewModel.state.collectAsState().value,
            onBack = { openList = null },
            onAdd = { t, d, dt -> detailViewModel.add(t, d, dt) },
            onToggle = { detailViewModel.toggle(it) },
            onSave = { todo, t, d, dt -> detailViewModel.update(todo, t, d, dt) },
            onDelete = { detailViewModel.delete(it) },
            onReorder = { id, before -> detailViewModel.reorder(id, before) },
            onAssign = { todo, assignee -> detailViewModel.assign(todo, assignee) },
            onToggleAssignedToMe = { detailViewModel.setAssignedToMeOnly(it) },
            currentEmail = remember { container.currentEmail() },
            onOpenMembers = { showingMembers = true },
        )
    }
}

/**
 * Wires a [com.example.todo.clientcore.membership.MembersViewModel] for one List
 * and renders the members + sharing screen. Kept here so both the shared app and
 * the desktop master-detail can reuse the same members UI.
 */
@Composable
fun MembersHost(
    container: AppContainer,
    list: ListDto,
    onBack: () -> Unit,
    onLeft: () -> Unit,
) {
    val isOwner = list.role == Role.OWNER
    val vm = remember(list.id) { container.membersViewModel(list.id, isOwner) }
    LaunchedEffect(list.id) { vm.load() }
    val currentEmail = remember { container.currentEmail() }
    MembersScreen(
        list = list,
        state = vm.state.collectAsState().value,
        isOwner = isOwner,
        currentEmail = currentEmail,
        onBack = onBack,
        onGenerate = { vm.generateInviteLink() },
        onRevoke = { vm.revokeInviteLink() },
        onRemove = { vm.removeMember(it) },
        onTransfer = { newOwnerId ->
            // After handing off ownership the caller is only an editor; return to
            // the index and reload so their role is refreshed everywhere.
            vm.transferOwnership(newOwnerId).invokeOnCompletion { onLeft() }
        },
        onLeave = {
            val me = vm.state.value.members.firstOrNull { m -> m.email == currentEmail }
            if (me != null) vm.removeMember(me.userId).also { job -> job.invokeOnCompletion { onLeft() } }
        },
    )
}

@Composable
fun ListsIndex(
    lists: List<ListDto>,
    error: String?,
    onOpen: (ListDto) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onSignOut: () -> Unit,
    onJoin: (String) -> Unit = {},
    onManageAccount: (() -> Unit)? = null,
    selectedId: String? = null,
) {
    var newName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Lists",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${lists.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New list") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onCreate(newName.trim()); newName = "" },
                enabled = newName.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Add") }
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        if (lists.isEmpty()) {
            Text(
                "No lists yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(lists, key = { it.id }) { list ->
                    ListRow(
                        list = list,
                        isSelected = list.id == selectedId,
                        onOpen = { onOpen(list) },
                        onRename = onRename,
                        onDelete = onDelete,
                    )
                }
            }
        }
        JoinListRow(onJoin = onJoin)

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            TextButton(onClick = onSignOut) {
                Text("Sign out", style = MaterialTheme.typography.bodySmall)
            }
            if (onManageAccount != null) {
                TextButton(onClick = onManageAccount) {
                    Text("Delete account", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun JoinListRow(onJoin: (String) -> Unit) {
    var invite by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = invite,
            onValueChange = { invite = it },
            label = { Text("Invite link or code") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { onJoin(invite.trim()); invite = "" },
            enabled = invite.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Join") }
    }
}

@Composable
private fun ListRow(
    list: ListDto,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(list.id) { mutableStateOf(list.name) }

    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Selection accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(28.dp)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        MaterialTheme.shapes.extraSmall,
                    ),
            )

            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                TextButton(onClick = { onRename(list.id, draft.trim()); editing = false }) { Text("Save") }
                TextButton(onClick = { draft = list.name; editing = false }) { Text("Cancel") }
            } else {
                TextButton(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                ) {
                    Text(
                        list.name,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { editing = true }, modifier = Modifier.size(32.dp)) {
                    Text("✎", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = { onDelete(list.id) }, modifier = Modifier.size(32.dp)) {
                    Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ListDetail(
    list: ListDto,
    state: ListDetailState,
    onBack: () -> Unit,
    onAdd: (String, String?, String?) -> Unit,
    onToggle: (TodoDto) -> Unit,
    onSave: (TodoDto, String, String?, String?) -> Unit,
    onDelete: (TodoDto) -> Unit,
    onReorder: (String, String?) -> Unit,
    onAssign: (TodoDto, String?) -> Unit = { _, _ -> },
    onToggleAssignedToMe: (Boolean) -> Unit = {},
    currentEmail: String? = null,
    onOpenMembers: (() -> Unit)? = null,
    showBackButton: Boolean = true,
) {
    val shown =
        if (state.assignedToMeOnly) state.todos.filter { it.assigneeEmail != null && it.assigneeEmail == currentEmail }
        else state.todos
    val active = shown.filterNot { it.completed }
    val done = shown.filter { it.completed }
    val doneCount = done.size
    val total = shown.size
    val progress = if (total == 0) 0f else doneCount.toFloat() / total
    var completedExpanded by remember { mutableStateOf(false) }
    // Reorder against a filtered subset would move a Todo relative to a partial
    // list, so it's only offered on the full, unfiltered view.
    val canReorder = !state.assignedToMeOnly

    Column(modifier = Modifier.fillMaxSize()) {
        // Header ribbon
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        if (showBackButton) {
                            TextButton(onClick = onBack) { Text("← Lists") }
                        }
                        Text(
                            list.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = if (showBackButton) 4.dp else 0.dp),
                        )
                    }
                    if (onOpenMembers != null) {
                        TextButton(onClick = onOpenMembers) {
                            Text(if (list.role == Role.OWNER) "Share" else "Members")
                        }
                    }
                }
                AddTodoRow(onAdd = onAdd)
            }
        }

        ErrorText(state.error)

        AssignedToMeToggle(state.assignedToMeOnly, onToggleAssignedToMe)

        if (shown.isEmpty()) {
            if (state.assignedToMeOnly) {
                Text(
                    "Nothing is assigned to you in this list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                EmptyTodos()
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Progress header
                item {
                    ProgressHeader(doneCount, total, progress)
                }

                // Active todos
                items(active, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        members = state.members,
                        currentEmail = currentEmail,
                        onToggle = { onToggle(todo) },
                        onSave = { title, desc, due -> onSave(todo, title, desc, due) },
                        onDelete = { onDelete(todo) },
                        onAssign = { onAssign(todo, it) },
                        onMoveUp = if (!canReorder) null else {
                            {
                                val idx = active.indexOfFirst { it.id == todo.id }
                                if (idx > 0) onReorder(todo.id, active[idx - 1].id)
                            }
                        },
                        onMoveDown = if (!canReorder) null else {
                            {
                                val idx = active.indexOfFirst { it.id == todo.id }
                                if (idx >= 0 && idx < active.size - 1)
                                    onReorder(todo.id, active.getOrNull(idx + 2)?.id)
                            }
                        },
                    )
                }

                // Completed section
                if (done.isNotEmpty()) {
                    item {
                        CompletedHeader(doneCount, completedExpanded) { completedExpanded = !completedExpanded }
                    }
                    if (completedExpanded) {
                        items(done, key = { it.id }) { todo ->
                            TodoRow(
                                todo = todo,
                                members = state.members,
                                currentEmail = currentEmail,
                                onToggle = { onToggle(todo) },
                                onSave = { title, desc, due -> onSave(todo, title, desc, due) },
                                onDelete = { onDelete(todo) },
                                onAssign = { onAssign(todo, it) },
                                onMoveUp = null,
                                onMoveDown = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressHeader(done: Int, total: Int, progress: Float) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$done of $total done",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(4.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.shapes.extraLarge,
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.extraLarge,
                    )
            )
        }
    }
}

@Composable
private fun CompletedHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
        onClick = onToggle,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(
                    "Completed ($count)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(
                if (expanded) "▲" else "▼",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddTodoRow(onAdd: (String, String?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New todo") },
            singleLine = true,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
        )
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    onAdd(title.trim(), description.trim().takeIf(String::isNotEmpty), dueDate.trim().takeIf(String::isNotEmpty))
                    title = ""; description = ""; dueDate = ""
                }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.padding(start = 12.dp),
        ) { Text("Add") }
    }
    TextButton(onClick = { expanded = !expanded }) {
        Text(
            if (expanded) "Less options" else "More options",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (expanded) {
        Column {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                label = { Text("Due date yyyy-MM-dd (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun TodoRow(
    todo: TodoDto,
    members: List<MemberDto>,
    currentEmail: String?,
    onToggle: () -> Unit,
    onSave: (String, String?, String?) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String?) -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var editing by remember(todo.id) { mutableStateOf(false) }
    var draftTitle by remember(todo.id) { mutableStateOf(todo.title) }
    var draftDesc by remember(todo.id) { mutableStateOf(todo.description ?: "") }
    var draftDue by remember(todo.id) { mutableStateOf(todo.dueDate ?: "") }
    var menuOpen by remember { mutableStateOf(false) }

    val rowAlpha = if (todo.completed) 0.6f else 1f
    val accentColor = if (todo.completed) {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth().alpha(rowAlpha),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.heightIn(min = 56.dp).padding(start = 0.dp),
        ) {
            // Left accent bar — the "notebook margin"
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .heightIn(min = 56.dp)
                    .background(accentColor),
            )

            // Custom circular checkbox
            Surface(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(24.dp)
                    .clickable { onToggle() },
                shape = MaterialTheme.shapes.extraLarge,
                color = if (todo.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                border = if (todo.completed) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
            ) {
                if (todo.completed) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (editing) {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
                    OutlinedTextField(value = draftTitle, onValueChange = { draftTitle = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = draftDesc, onValueChange = { draftDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    OutlinedTextField(value = draftDue, onValueChange = { draftDue = it }, label = { Text("Due date") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        TextButton(onClick = {
                            // Send blank (not null) for emptied fields so the server
                            // clears them; null would mean "leave unchanged".
                            onSave(draftTitle.trim(), draftDesc.trim(), draftDue.trim())
                            editing = false
                        }) { Text("Save") }
                        TextButton(onClick = {
                            draftTitle = todo.title; draftDesc = todo.description ?: ""; draftDue = todo.dueDate ?: ""; editing = false
                        }) { Text("Cancel") }
                    }
                }
            } else {
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp, top = 10.dp, bottom = 10.dp)) {
                    Text(
                        todo.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (todo.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    todo.description?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    todo.dueDate?.let {
                        Text(
                            "Due $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AssigneeChip(
                        todo = todo,
                        members = members,
                        currentEmail = currentEmail,
                        onAssign = onAssign,
                    )
                }

                // Reorder buttons — only for reorderable (active) rows.
                if (onMoveUp != null) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(36.dp)) {
                        Text("↑", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (onMoveDown != null) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(36.dp)) {
                        Text("↓", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Overflow menu for Edit + Delete
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                        Text("⋯", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit") },
                            onClick = { menuOpen = false; editing = true },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** A small tappable assignee label that opens a member picker (slice 6). */
@Composable
private fun AssigneeChip(
    todo: TodoDto,
    members: List<MemberDto>,
    currentEmail: String?,
    onAssign: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val label = when {
        todo.assigneeEmail == null -> "Assign"
        todo.assigneeEmail == currentEmail -> "Assigned to you"
        else -> "Assigned to ${todo.assigneeEmail}"
    }
    Box {
        TextButton(
            onClick = { open = true },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        ) {
            Text(
                "◍ $label",
                style = MaterialTheme.typography.labelSmall,
                color = if (todo.assigneeEmail == null) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Unassigned") },
                onClick = { open = false; onAssign(null) },
            )
            members.forEach { member ->
                val me = member.email == currentEmail
                DropdownMenuItem(
                    text = { Text(member.email + if (me) " (you)" else "") },
                    onClick = { open = false; onAssign(member.userId) },
                )
            }
        }
    }
}

/** The "Assigned to me" filter toggle shown above the Todo list (slice 6). */
@Composable
private fun AssignedToMeToggle(active: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = MaterialTheme.shapes.small,
            onClick = { onToggle(!active) },
        ) {
            Text(
                if (active) "✓ Assigned to me" else "Assigned to me",
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun EmptyTodos() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Distinctive empty-state mark — a soft circle with a pencil glyph
        Surface(
            modifier = Modifier.size(72.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "✎",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            "Start your list",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            "Add your first todo above to get going.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * The members + sharing screen for one List (slice 5). Owners manage the Invite
 * Link (generate/revoke) and remove members; editors see the roster and can leave.
 */
@Composable
fun MembersScreen(
    list: ListDto,
    state: MembersState,
    isOwner: Boolean,
    currentEmail: String?,
    onBack: () -> Unit,
    onGenerate: () -> Unit,
    onRevoke: () -> Unit,
    onRemove: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onLeave: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onBack) { Text("← Back") }
                Text(
                    "${list.name} · Members",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        ErrorText(state.error)

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            if (isOwner) {
                InviteLinkCard(
                    token = state.inviteLink?.token,
                    expiresAt = state.inviteLink?.expiresAt,
                    onGenerate = onGenerate,
                    onRevoke = onRevoke,
                )
            }

            Text(
                "People",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.members, key = { it.userId }) { member ->
                    MemberRow(
                        member = member,
                        isMe = member.email == currentEmail,
                        canManage = isOwner && member.role != Role.OWNER,
                        onRemove = { onRemove(member.userId) },
                        onTransfer = { onTransfer(member.userId) },
                    )
                }
            }

            if (!isOwner) {
                TextButton(
                    onClick = onLeave,
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Leave this list", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun InviteLinkCard(
    token: String?,
    expiresAt: String?,
    onGenerate: () -> Unit,
    onRevoke: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Invite link", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            if (token == null) {
                Text(
                    "No active invite link. Generate one to let others join as editors.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Button(onClick = onGenerate, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Generate link")
                }
            } else {
                OutlinedTextField(
                    value = token,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Share this code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                expiresAt?.let {
                    Text(
                        "Expires $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(onClick = onGenerate) { Text("Regenerate") }
                    TextButton(onClick = onRevoke) {
                        Text("Revoke", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: MemberDto,
    isMe: Boolean,
    canManage: Boolean,
    onRemove: () -> Unit,
    onTransfer: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    member.email + if (isMe) " (you)" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (member.role == Role.OWNER) "Owner" else "Editor",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canManage) {
                TextButton(onClick = onTransfer) {
                    Text("Make owner", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Text("×", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/** Wires an [com.example.todo.clientcore.account.AccountViewModel] for the delete-account flow. */
@Composable
fun AccountHost(container: AppContainer, onBack: () -> Unit, onDeleted: () -> Unit) {
    val vm = remember { container.accountViewModel() }
    LaunchedEffect(Unit) { vm.loadBlockers() }
    AccountScreen(
        state = vm.state.collectAsState().value,
        onBack = onBack,
        onDelete = { vm.deleteAccount(onDeleted) },
    )
}

/**
 * Delete-account screen (slice 7). Lists the shared Lists blocking deletion, or
 * offers a confirm-to-delete when there are none.
 */
@Composable
fun AccountScreen(
    state: com.example.todo.clientcore.account.AccountState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back") }
            Text("Delete account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        ErrorText(state.error)

        if (state.blockingLists.isNotEmpty()) {
            Text(
                "Transfer or delete these shared lists before deleting your account:",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            LazyColumn(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.blockingLists, key = { it.id }) { list ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = MaterialTheme.shapes.small,
                    ) {
                        Text(list.name, modifier = Modifier.padding(12.dp))
                    }
                }
            }
        } else {
            Text(
                "This permanently deletes your account and your solo lists. Shared lists you own must be transferred or deleted first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (!confirming) {
                Button(
                    onClick = { confirming = true },
                    modifier = Modifier.padding(top = 16.dp),
                ) { Text("Delete my account") }
            } else {
                Text(
                    "Are you sure? This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    Button(
                        onClick = onDelete,
                        enabled = !state.loading,
                    ) { Text("Yes, delete", color = MaterialTheme.colorScheme.onError) }
                    TextButton(onClick = { confirming = false }) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
}
