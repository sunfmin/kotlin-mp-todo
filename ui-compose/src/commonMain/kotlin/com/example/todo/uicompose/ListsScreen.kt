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
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.AppContainer
import com.example.todo.common.ListDto

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
        ListDetail(list = current, onBack = { openList = null })
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
private fun ListDetail(list: ListDto, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Lists") }
            Text(list.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            "No todos yet.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
    }
}
