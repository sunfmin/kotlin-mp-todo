package com.example.todo.server.account

import com.example.todo.common.ListDto
import com.example.todo.common.Role
import com.example.todo.server.db.Lists
import com.example.todo.server.db.Memberships
import com.example.todo.server.db.Users
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

/**
 * Account lifecycle (slice 7, ADR-0009). Deleting a User is blocked while they
 * still own any *shared* List (a List with other members); those must be
 * transferred or deleted first. Solo-owned Lists are removed with the account.
 * Todos assigned to the User elsewhere are auto-unassigned by the assignee FK's
 * ON DELETE SET NULL, and their editor memberships cascade away.
 */
class AccountService {

    /** The shared Lists the User owns that block account deletion (empty = deletable). */
    fun blockingLists(userId: UUID): List<ListDto> = transaction {
        ownedLists(userId).filter { hasOtherMembers(it) }
            .map { row ->
                ListDto(row[Lists.id].toString(), row[Lists.name], Role.OWNER, row[Lists.createdAt].toString())
            }
    }

    /** Delete the account, or throw if it still owns shared Lists. */
    fun deleteAccount(userId: UUID) = transaction {
        val owned = ownedLists(userId)
        require(owned.none { hasOtherMembers(it) }) {
            "Account still owns shared lists; transfer or delete them first."
        }
        // Solo-owned Lists (no other members) go with the account; cascades remove
        // their todos, memberships, and invite links.
        owned.forEach { row ->
            Lists.deleteWhere { Lists.id eq row[Lists.id] }
        }
        // Removing the User cascades their editor memberships and refresh tokens,
        // and NULLs any Todo they were assigned in other members' Lists.
        Users.deleteWhere { Users.id eq userId }
    }

    private fun ownedLists(userId: UUID) =
        Lists.selectAll().where { Lists.ownerId eq userId }.toList()

    private fun hasOtherMembers(listRow: org.jetbrains.exposed.sql.ResultRow): Boolean =
        Memberships.selectAll().where { Memberships.listId eq listRow[Lists.id] }.any()
}
