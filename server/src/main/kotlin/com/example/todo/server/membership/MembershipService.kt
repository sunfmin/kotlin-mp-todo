package com.example.todo.server.membership

import com.example.todo.common.InviteLinkDto
import com.example.todo.common.InvitePreviewDto
import com.example.todo.common.ListDto
import com.example.todo.common.MemberDto
import com.example.todo.common.Role
import com.example.todo.server.DomainException
import com.example.todo.server.db.InviteLinks
import com.example.todo.server.db.Lists
import com.example.todo.server.db.Memberships
import com.example.todo.server.db.Users
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.UUID

/**
 * Sharing & membership (slice 5, ADR-0004). Owns the Invite Link lifecycle
 * (one active link per List, regenerate/revoke/expiry), the join-as-Editor flow,
 * the member list, and member removal/leave. All owner-only actions authorize
 * through [Members]; the server is the final authority (ADR-0002).
 */
class MembershipService(private val clock: () -> Instant = { Clock.System.now() }) {

    /** Everyone in the List — Owner first, then Editors oldest-first. Any member may view. */
    fun members(userId: UUID, listId: UUID): List<MemberDto> = transaction {
        Members.requireMember(userId, listId)
        val ownerId = Lists.selectAll().where { Lists.id eq listId }.single()[Lists.ownerId]
        val ownerEmail = Users.selectAll().where { Users.id eq ownerId }.single()[Users.email]

        val editors = (Memberships innerJoin Users)
            .selectAll().where { Memberships.listId eq listId }
            .orderBy(Memberships.createdAt to SortOrder.ASC)
            .map { MemberDto(it[Users.id].toString(), it[Users.email], Role.EDITOR) }

        listOf(MemberDto(ownerId.toString(), ownerEmail, Role.OWNER)) + editors
    }

    /** The current active, unexpired Invite Link, or null if none. Owner only. */
    fun getInviteLink(userId: UUID, listId: UUID): InviteLinkDto? = transaction {
        Members.requireOwner(userId, listId)
        activeLink(listId)?.let { it.toInviteDto() }
    }

    /** Generate a fresh link, revoking any prior active one (ADR-0004). Owner only. */
    fun generateInviteLink(userId: UUID, listId: UUID, expiresAt: String?): InviteLinkDto =
        transaction {
            Members.requireOwner(userId, listId)
            val expiry = parseExpiry(expiresAt)
            // Revoke the prior active link so the partial unique index holds.
            InviteLinks.update({ (InviteLinks.listId eq listId) and (InviteLinks.active eq true) }) {
                it[InviteLinks.active] = false
            }
            val token = UUID.randomUUID()
            InviteLinks.insert {
                it[InviteLinks.token] = token
                it[InviteLinks.listId] = listId
                it[InviteLinks.active] = true
                it[InviteLinks.expiresAt] = expiry
                it[InviteLinks.createdAt] = clock()
            }
            InviteLinkDto(token.toString(), expiry?.toString())
        }

    /** Revoke the active link so it can no longer be used to join. Owner only. */
    fun revokeInviteLink(userId: UUID, listId: UUID): Unit = transaction {
        Members.requireOwner(userId, listId)
        InviteLinks.update({ (InviteLinks.listId eq listId) and (InviteLinks.active eq true) }) {
            it[active] = false
        }
    }

    /** What the joiner sees for a valid link, or 404 if the link is not joinable. */
    fun preview(token: UUID): InvitePreviewDto = transaction {
        val link = joinableLink(token) ?: throw DomainException.notFound("This invite link is no longer valid.")
        val list = Lists.selectAll().where { Lists.id eq link[InviteLinks.listId] }.single()
        InvitePreviewDto(list[Lists.id].toString(), list[Lists.name])
    }

    /** Join the List an active link points to as an EDITOR (idempotent). Requires auth. */
    fun join(userId: UUID, token: UUID): ListDto = transaction {
        val link = joinableLink(token) ?: throw DomainException.notFound("This invite link is no longer valid.")
        val listId = link[InviteLinks.listId]
        val existingRole = Members.roleOf(userId, listId)
        if (existingRole == null) {
            Memberships.insert {
                it[Memberships.listId] = listId
                it[Memberships.userId] = userId
                it[createdAt] = clock()
            }
        }
        val list = Lists.selectAll().where { Lists.id eq listId }.single()
        ListDto(
            id = listId.toString(),
            name = list[Lists.name],
            role = existingRole ?: Role.EDITOR,
            createdAt = list[Lists.createdAt].toString(),
        )
    }

    /**
     * Remove a member, or leave a List. The Owner may remove any Editor; an
     * Editor may remove only themselves (leave). The Owner cannot be removed and
     * cannot leave here — ownership transfer is slice 7.
     */
    fun removeMember(callerId: UUID, listId: UUID, targetUserId: UUID): Unit = transaction {
        val callerRole = Members.requireMember(callerId, listId)
        val leaving = callerId == targetUserId

        if (leaving) {
            if (callerRole == Role.OWNER) {
                throw DomainException.conflict(
                    "The owner must transfer ownership or delete the List before leaving.",
                )
            }
        } else {
            // Removing someone else is an owner-only action.
            if (callerRole != Role.OWNER) throw DomainException.forbidden("Only the List owner can remove members.")
            when (Members.roleOf(targetUserId, listId)) {
                Role.OWNER -> throw DomainException.badRequest("The owner cannot be removed from their own List.")
                null -> throw DomainException.notFound("That member is not in this List.")
                else -> Unit
            }
        }

        Memberships.deleteWhere {
            (Memberships.listId eq listId) and (Memberships.userId eq targetUserId)
        }
    }

    /** The active, unexpired link for a List (owner-facing lookup). */
    private fun activeLink(listId: UUID): ResultRow? {
        val now = clock()
        return InviteLinks.selectAll()
            .where {
                (InviteLinks.listId eq listId) and (InviteLinks.active eq true) and
                    (InviteLinks.expiresAt.isNull() or (InviteLinks.expiresAt greater now))
            }
            .firstOrNull()
    }

    /** An active, unexpired link by token (join/preview lookup). */
    private fun joinableLink(token: UUID): ResultRow? {
        val now = clock()
        return InviteLinks.selectAll()
            .where {
                (InviteLinks.token eq token) and (InviteLinks.active eq true) and
                    (InviteLinks.expiresAt.isNull() or (InviteLinks.expiresAt greater now))
            }
            .firstOrNull()
    }

    private fun parseExpiry(raw: String?): Instant? {
        val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(trimmed) }.getOrNull()
            ?: throw DomainException.badRequest("Invalid expiry; expected an ISO-8601 instant.")
    }

    private fun ResultRow.toInviteDto() =
        InviteLinkDto(this[InviteLinks.token].toString(), this[InviteLinks.expiresAt]?.toString())
}
