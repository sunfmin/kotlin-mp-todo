package com.example.todo.clientcore.net

import com.example.todo.common.ApiRoutes
import com.example.todo.common.AssignTodoRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ReorderTodoRequest
import com.example.todo.common.TodoDto
import com.example.todo.common.UpdateTodoRequest
import io.ktor.client.call.body

/** Todo CRUD + reorder against the server (slice 4), authenticated via [AuthorizedApi]. */
class TodosApi(private val api: AuthorizedApi) {

    suspend fun all(listId: String): List<TodoDto> = api.get(ApiRoutes.todos(listId)).body()

    suspend fun create(listId: String, req: CreateTodoRequest): TodoDto =
        api.post(ApiRoutes.todos(listId), req).body()

    suspend fun update(listId: String, todoId: String, req: UpdateTodoRequest): TodoDto =
        api.put(ApiRoutes.todo(listId, todoId), req).body()

    suspend fun delete(listId: String, todoId: String) {
        api.delete(ApiRoutes.todo(listId, todoId))
    }

    suspend fun reorder(listId: String, todoId: String, beforeId: String?): TodoDto =
        api.patch(ApiRoutes.todoReorder(listId, todoId), ReorderTodoRequest(beforeId)).body()

    /** Assign the Todo to a member, or unassign it when [assigneeUserId] is null (slice 6). */
    suspend fun assign(listId: String, todoId: String, assigneeUserId: String?): TodoDto =
        api.put(ApiRoutes.todoAssignee(listId, todoId), AssignTodoRequest(assigneeUserId)).body()
}
