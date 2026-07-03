package com.example.todo.clientcore.net

import com.example.todo.common.AccountDeletionInfo
import com.example.todo.common.ApiRoutes
import com.example.todo.common.ListDto
import io.ktor.client.call.body

/** Account lifecycle against the server (slice 7), authenticated via [AuthorizedApi]. */
class AccountApi(private val api: AuthorizedApi) {

    /** Shared Lists that currently block account deletion (empty = deletable). */
    suspend fun deletionBlockers(): List<ListDto> =
        api.get(ApiRoutes.ACCOUNT_DELETION_BLOCKERS).body<AccountDeletionInfo>().blockingLists

    /** Delete the account. Throws [ApiException] (409) if still blocked. */
    suspend fun deleteAccount() {
        api.delete(ApiRoutes.ACCOUNT)
    }
}
