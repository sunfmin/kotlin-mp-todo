package com.example.todo.common

import kotlinx.serialization.Serializable

/**
 * Why an account cannot be deleted yet (slice 7, ADR-0009). [blockingLists] are
 * the shared Lists the User still owns; they must transfer or delete each before
 * their account can be removed. Empty means deletion is allowed.
 */
@Serializable
data class AccountDeletionInfo(
    val blockingLists: List<ListDto> = emptyList(),
)
