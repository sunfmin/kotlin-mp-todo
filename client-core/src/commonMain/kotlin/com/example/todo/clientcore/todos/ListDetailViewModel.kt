package com.example.todo.clientcore.todos

import com.example.todo.clientcore.net.TodosApi
import com.example.todo.common.TodoDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** UI-facing state for a single List's detail (its Todos), slice 4. */
data class ListDetailState(
    val loading: Boolean = false,
    val todos: List<TodoDto> = emptyList(),
    val error: String? = null,
)

/**
 * Drives the List-detail screen shared by all four clients (ADR-0001). Every
 * mutation refetches the Todo list from the server so the client never holds a
 * divergent local copy. Actions return the launched [Job] so tests can await
 * them deterministically.
 */
class ListDetailViewModel(
    private val listId: String,
    private val api: TodosApi,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ListDetailState())
    val state: StateFlow<ListDetailState> = _state.asStateFlow()

    fun load(): Job = scope.launch {
        _state.value = _state.value.copy(loading = true, error = null)
        _state.value = try {
            ListDetailState(loading = false, todos = api.all(listId))
        } catch (e: Exception) {
            _state.value.copy(loading = false, error = e.message ?: "Could not load todos")
        }
    }

    fun add(title: String, description: String? = null, dueDate: String? = null): Job =
        mutateThenReload { api.create(listId, com.example.todo.common.CreateTodoRequest(title, description, dueDate)) }

    fun toggle(todo: TodoDto): Job =
        mutateThenReload { api.update(listId, todo.id, com.example.todo.common.UpdateTodoRequest(completed = !todo.completed)) }

    fun update(todo: TodoDto, title: String, description: String?, dueDate: String?): Job =
        mutateThenReload { api.update(listId, todo.id, com.example.todo.common.UpdateTodoRequest(title, description, dueDate)) }

    fun delete(todo: TodoDto): Job = mutateThenReload { api.delete(listId, todo.id) }

    /** Move [todoId] to immediately before [beforeId] (null = end of list). */
    fun reorder(todoId: String, beforeId: String?): Job = mutateThenReload {
        api.reorder(listId, todoId, beforeId)
    }

    private fun mutateThenReload(op: suspend () -> Unit): Job = scope.launch {
        _state.value = _state.value.copy(error = null)
        try {
            op()
            _state.value = _state.value.copy(todos = api.all(listId))
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message ?: "Something went wrong")
        }
    }
}
