package com.example.todo.uicompose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.AppContainer
import com.example.todo.common.ListDto
import com.example.todo.common.TodoDto

/**
 * The authenticated Lists app: index of the user's Lists with create/rename/delete,
 * and a detail view opened by tapping a List. In slice 3 the detail is an empty
 * placeholder; slice 4 fills it with Todos.
 */
@Composable
fun ListsApp(container: AppContainer, onSignOut: () -> Unit) {
    val viewModel = remember { container.listsViewModel() }
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    var openList by remember { mutableStateOf<ListDto?>(null) }
    // Keep the open List's name fresh after a rename.
    val current = openList?.let { o -> state.lists.firstOrNull { it.id == o.id } ?: o }

    if (current == null) {
        ListsIndex(
            lists = state.lists,
            error = state.error,
            onOpen = { openList = it },
            onCreate = { viewModel.create(it) },
            onRename = { id, name -> viewModel.rename(id, name) },
            onDelete = { id -> viewModel.delete(id) },
            onSignOut = onSignOut,
        )
    } else {
        ListDetail(
            list = current,
            container = container,
            onBack = { openList = null },
        )
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
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Your lists", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New list name") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { onCreate(newName.trim()); newName = "" },
                enabled = newName.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Add") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        if (lists.isEmpty()) {
            Text(
                "No lists yet. Create your first one above.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                items(lists, key = { it.id }) { list ->
                    ListRow(list, onOpen = { onOpen(list) }, onRename = onRename, onDelete = onDelete)
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
    var editing by remember { mutableStateOf(false) }
    var draft by remember(list.id) { mutableStateOf(list.name) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { onRename(list.id, draft.trim()); editing = false }) { Text("Save") }
                    TextButton(onClick = { draft = list.name; editing = false }) { Text("Cancel") }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpen) {
                        Text(list.name, fontWeight = FontWeight.Medium)
                    }
                    Row {
                        TextButton(onClick = { editing = true }) { Text("Rename") }
                        TextButton(onClick = { onDelete(list.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListDetail(list: ListDto, container: AppContainer, onBack: () -> Unit) {
    val viewModel = remember(list.id) { container.listDetailViewModel(list.id) }
    LaunchedEffect(list.id) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Lists") }
            Text(list.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
        }
        AddTodoRow(onAdd = { title, desc, due -> viewModel.add(title, desc, due) })
        error(state.error)
        if (state.todos.isEmpty()) {
            Text(
                "No todos yet. Add your first one above.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                items(state.todos, key = { it.id }) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { viewModel.toggle(todo) },
                        onSave = { title, desc, due -> viewModel.update(todo, title, desc, due) },
                        onDelete = { viewModel.delete(todo) },
                        onMoveUp = {
                            val idx = state.todos.indexOfFirst { it.id == todo.id }
                            if (idx > 0) viewModel.reorder(todo.id, state.todos[idx - 1].id)
                        },
                        onMoveDown = {
                            val idx = state.todos.indexOfFirst { it.id == todo.id }
                            if (idx >= 0 && idx < state.todos.size - 1)
                                viewModel.reorder(todo.id, state.todos.getOrNull(idx + 2)?.id)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTodoRow(onAdd: (String, String?, String?) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("New todo") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                if (title.isNotBlank()) {
                    onAdd(
                        title.trim(),
                        description.trim().takeIf(String::isNotEmpty),
                        dueDate.trim().takeIf(String::isNotEmpty),
                    )
                    title = ""; description = ""; dueDate = ""
                }
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.padding(start = 8.dp),
        ) { Text("Add") }
    }
    TextButton(onClick = { expanded = !expanded }) {
        Text(if (expanded) "Less options" else "More options")
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

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (editing) {
                OutlinedTextField(
                    value = draftTitle,
                    onValueChange = { draftTitle = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draftDesc,
                    onValueChange = { draftDesc = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                OutlinedTextField(
                    value = draftDue,
                    onValueChange = { draftDue = it },
                    label = { Text("Due date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = {
                        onSave(draftTitle.trim(), draftDesc.trim().takeIf(String::isNotEmpty), draftDue.trim().takeIf(String::isNotEmpty))
                        editing = false
                    }) { Text("Save") }
                    TextButton(onClick = {
                        draftTitle = todo.title; draftDesc = todo.description ?: ""; draftDue = todo.dueDate ?: ""
                        editing = false
                    }) { Text("Cancel") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(checked = todo.completed, onCheckedChange = { onToggle() })
                    Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            todo.title,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (todo.completed) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        todo.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        todo.dueDate?.let { Text("Due: $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    TextButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = onMoveUp) { Text("↑") }
                    TextButton(onClick = onMoveDown) { Text("↓") }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
private fun error(message: String?) {
    if (message != null) {
        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
    }
}
