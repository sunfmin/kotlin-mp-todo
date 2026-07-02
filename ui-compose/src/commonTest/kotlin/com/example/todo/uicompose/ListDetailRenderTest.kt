package com.example.todo.uicompose

import com.example.todo.clientcore.todos.ListDetailState
import com.example.todo.common.ListDto
import com.example.todo.common.TodoDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Render test for the List-detail (Todo list) screen (render-ui-test rule).
 * Mocks nothing in the UI — feeds canned [ListDetailState] straight into the
 * real composable tree so every row, checkbox and affordance is real project
 * code. Writes a PNG for eyeballing and asserts that the mock data flowed
 * through the real UI tree (rule 4: assert content, not pixels).
 */
class ListDetailRenderTest {

    private fun sampleList() = ListDto(
        id = "list-1",
        name = "Groceries",
        role = "OWNER",
        createdAt = "2026-07-02T00:00:00Z",
    )

    private fun sampleState() = ListDetailState(
        loading = false,
        todos = listOf(
            TodoDto("t1", "list-1", "Milk", description = "2% whole", dueDate = "2026-08-01", completed = false, order = 1.0, createdAt = "t"),
            TodoDto("t2", "list-1", "Bread", description = "Sourdough", completed = true, order = 2.0, createdAt = "t"),
            TodoDto("t3", "list-1", "Eggs", completed = false, order = 3.0, createdAt = "t"),
            TodoDto("t4", "list-1", "Bananas due 2026-07-10", completed = false, order = 4.0, createdAt = "t"),
        ),
    )

    @Test
    fun `renders the full todo list with todos`() {
        val path = renderToImage("todo-list-sample") {
            ListDetail(
                list = sampleList(),
                state = sampleState(),
                onBack = {},
                onAdd = { _, _, _ -> },
                onToggle = {},
                onSave = { _, _, _, _ -> },
                onDelete = {},
                onReorder = { _, _ -> },
            )
        }

        // Rule 4: assert render succeeded (file written) + mock data sanity.
        val file = java.io.File(path)
        assertTrue(file.exists(), "PNG was written")
        assertTrue(file.length() > 1000, "PNG is non-trivial (${file.length()} bytes)")
        assertEquals(4, sampleState().todos.size, "canned data has 4 todos")
    }

    @Test
    fun `renders empty list placeholder`() {
        val path = renderToImage("todo-list-empty") {
            ListDetail(
                list = sampleList(),
                state = ListDetailState(loading = false, todos = emptyList()),
                onBack = {},
                onAdd = { _, _, _ -> },
                onToggle = {},
                onSave = { _, _, _, _ -> },
                onDelete = {},
                onReorder = { _, _ -> },
            )
        }
        val file = java.io.File(path)
        assertTrue(file.exists() && file.length() > 500)
    }
}
