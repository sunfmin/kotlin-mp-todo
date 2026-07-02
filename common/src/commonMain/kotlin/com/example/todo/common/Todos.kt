package com.example.todo.common

import kotlinx.serialization.Serializable

/**
 * A Todo within a List (slice 4). Belongs to exactly one List, carries an
 * [order] key for manual ordering (fractional indexing — see ADR-0007), and
 * is never orphaned.
 */
@Serializable
data class TodoDto(
    val id: String,
    val listId: String,
    val title: String,
    val description: String? = null,
    /** ISO-8601 date (yyyy-MM-dd), or null when no due date is set. */
    val dueDate: String? = null,
    val completed: Boolean = false,
    /** Fractional ordering key within the List; sort ascending. */
    val order: Double,
    val createdAt: String,
)

@Serializable
data class CreateTodoRequest(
    val title: String,
    val description: String? = null,
    val dueDate: String? = null,
)

@Serializable
data class UpdateTodoRequest(
    val title: String? = null,
    val description: String? = null,
    val dueDate: String? = null,
    val completed: Boolean? = null,
)

@Serializable
data class ReorderTodoRequest(
    /**
     * Insert the Todo immediately before the Todo with this id. Null means
     * move to the end of the List. Must be a Todo in the same List.
     */
    val beforeId: String? = null,
)
