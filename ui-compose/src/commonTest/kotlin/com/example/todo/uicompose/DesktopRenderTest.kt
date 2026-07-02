package com.example.todo.uicompose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.todo.clientcore.lists.ListsState
import com.example.todo.clientcore.todos.ListDetailState
import com.example.todo.common.ListDto
import com.example.todo.common.Role
import com.example.todo.common.TodoDto
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Render test for the desktop master-detail layout: Lists sidebar + Todo detail
 * side by side. Verifies the full desktop viewport renders both panes with
 * real data flowing through.
 */
class DesktopRenderTest {

    private fun sampleLists() = listOf(
        ListDto("l1", "Groceries", Role.OWNER, "2026-07-01T00:00:00Z"),
        ListDto("l2", "Work tasks", Role.OWNER, "2026-07-02T00:00:00Z"),
        ListDto("l3", "Reading list", Role.OWNER, "2026-07-03T00:00:00Z"),
    )

    private fun sampleTodos() = listOf(
        TodoDto("t1", "l1", "Milk", description = "2% whole", dueDate = "2026-08-01", completed = false, order = 1.0, createdAt = "t"),
        TodoDto("t2", "l1", "Bread", description = "Sourdough", completed = true, order = 2.0, createdAt = "t"),
        TodoDto("t3", "l1", "Eggs", completed = false, order = 3.0, createdAt = "t"),
    )

    @Test
    fun `renders master detail with selected list`() {
        val path = renderToImage("desktop-master-detail") {
            MaterialTheme {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar
                    Surface(
                        modifier = Modifier.width(280.dp).fillMaxSize(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ) {
                        ListsIndex(
                            lists = sampleLists(),
                            error = null,
                            onOpen = {},
                            onCreate = {},
                            onRename = { _, _ -> },
                            onDelete = {},
                            onSignOut = {},
                            selectedId = sampleLists().first().id,
                        )
                    }
                    // Detail
                    Surface(modifier = Modifier.fillMaxSize()) {
                        ListDetail(
                            list = sampleLists().first(),
                            state = ListDetailState(loading = false, todos = sampleTodos()),
                            onBack = {},
                            onAdd = { _, _, _ -> },
                            onToggle = {},
                            onSave = { _, _, _, _ -> },
                            onDelete = {},
                            onReorder = { _, _ -> },
                            showBackButton = false,
                        )
                    }
                }
            }
        }
        val file = java.io.File(path)
        assertTrue(file.exists() && file.length() > 1000, "desktop PNG written (${file.length()} bytes)")
    }

    @Test
    fun `renders sidebar with empty lists`() {
        val path = renderToImage("desktop-sidebar-empty") {
            MaterialTheme {
                ListsIndex(
                    lists = emptyList(),
                    error = null,
                    onOpen = {},
                    onCreate = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onSignOut = {},
                )
            }
        }
        val file = java.io.File(path)
        assertTrue(file.exists() && file.length() > 500)
    }

    @Test
    fun `renders detail empty state`() {
        val path = renderToImage("desktop-detail-empty") {
            MaterialTheme {
                ListDetail(
                    list = ListDto("l1", "Work", Role.OWNER, "t"),
                    state = ListDetailState(loading = false, todos = emptyList()),
                    onBack = {},
                    onAdd = { _, _, _ -> },
                    onToggle = {},
                    onSave = { _, _, _, _ -> },
                    onDelete = {},
                    onReorder = { _, _ -> },
                )
            }
        }
        val file = java.io.File(path)
        assertTrue(file.exists() && file.length() > 500)
    }
}
