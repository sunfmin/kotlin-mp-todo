package com.example.todo.server.routes

import com.example.todo.common.AccountDeletionInfo
import com.example.todo.common.ApiRoutes
import com.example.todo.server.account.AccountService
import com.example.todo.server.plugins.AUTH_JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get

/**
 * Account lifecycle routes (slice 7). Deletion is blocked while the User owns
 * shared Lists; the 409 body carries the blocking Lists so the client can guide
 * the user to transfer or delete them first (ADR-0009).
 */
fun Route.accountRoutes(account: AccountService) {
    authenticate(AUTH_JWT) {
        get(ApiRoutes.ACCOUNT_DELETION_BLOCKERS) {
            call.respond(AccountDeletionInfo(account.blockingLists(call.userId())))
        }
        delete(ApiRoutes.ACCOUNT) {
            val blockers = account.blockingLists(call.userId())
            if (blockers.isNotEmpty()) {
                call.respond(HttpStatusCode.Conflict, AccountDeletionInfo(blockers))
            } else {
                account.deleteAccount(call.userId())
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
