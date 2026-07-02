package com.example.todo.clientcore.net

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateListRequest
import com.example.todo.common.ListDto
import com.example.todo.common.RenameListRequest
import io.ktor.client.call.body

/** Lists CRUD against the server (slice 3), authenticated via [AuthorizedApi]. */
class ListsApi(private val api: AuthorizedApi) {

    suspend fun all(): List<ListDto> = api.get(ApiRoutes.LISTS).body()

    suspend fun create(name: String): ListDto =
        api.post(ApiRoutes.LISTS, CreateListRequest(name)).body()

    suspend fun get(id: String): ListDto = api.get(ApiRoutes.list(id)).body()

    suspend fun rename(id: String, name: String): ListDto =
        api.put(ApiRoutes.list(id), RenameListRequest(name)).body()

    suspend fun delete(id: String) {
        api.delete(ApiRoutes.list(id))
    }
}
