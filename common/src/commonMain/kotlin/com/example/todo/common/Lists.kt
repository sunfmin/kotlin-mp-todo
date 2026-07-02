package com.example.todo.common

import kotlinx.serialization.Serializable

/**
 * A List the signed-in User can access, with the caller's [role] in it (ADR-0005).
 * [role] is one of [Role]; in slice 3 every List the caller sees is one they OWN,
 * but the field is on the contract from the start so the UI stays stable as
 * sharing (slice 5) introduces EDITOR members.
 */
@Serializable
data class ListDto(
    val id: String,
    val name: String,
    val role: String,
    /** ISO-8601 instant the List was created. */
    val createdAt: String,
)

@Serializable
data class CreateListRequest(val name: String)

@Serializable
data class RenameListRequest(val name: String)

/** The Role a member holds in a List (glossary: Owner / Editor). */
object Role {
    const val OWNER = "OWNER"
    const val EDITOR = "EDITOR"
}
