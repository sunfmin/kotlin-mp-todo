package com.example.todo.common

import kotlinx.serialization.Serializable

/**
 * A member of a List (slice 5). The Owner is derived from the List's `owner_id`
 * and every other member is an EDITOR (ADR-0004: no viewer role). [role] is one
 * of [Role].
 */
@Serializable
data class MemberDto(
    val userId: String,
    val email: String,
    val role: String,
)

/**
 * The single active Invite Link for a List (ADR-0004). [token] is the opaque,
 * unguessable value embedded in the shareable URL; following it joins the List
 * as an EDITOR. [expiresAt] is an optional ISO-8601 instant after which the link
 * no longer allows joining (null = never expires).
 */
@Serializable
data class InviteLinkDto(
    val token: String,
    val expiresAt: String? = null,
)

/**
 * Transfer a List's ownership to another current member (slice 7). The named
 * member becomes the sole Owner and the previous Owner becomes an Editor,
 * preserving the "exactly one Owner" invariant (ADR-0009).
 */
@Serializable
data class TransferOwnershipRequest(
    val newOwnerUserId: String,
)

/** Regenerate/generate the List's Invite Link, revoking any prior one. */
@Serializable
data class CreateInviteLinkRequest(
    /** Optional ISO-8601 instant; null clears/omits expiry. */
    val expiresAt: String? = null,
)

/**
 * What a joiner sees before committing to join, so the UI can show "Join
 * <listName>?" for a valid link without leaking more than the List's name.
 */
@Serializable
data class InvitePreviewDto(
    val listId: String,
    val listName: String,
)

/**
 * Extract the invite token from a value a user pasted, which may be a full invite
 * URL (`.../invite/<token>` or `.../invite/<token>/join`) or a bare token. Kept in
 * the shared contract so every client parses invites the same way.
 */
fun inviteTokenOf(raw: String): String {
    val trimmed = raw.trim().trimEnd('/')
    val marker = "${ApiRoutes.INVITE}/"
    val idx = trimmed.lastIndexOf(marker)
    val tail = if (idx >= 0) trimmed.substring(idx + marker.length) else trimmed
    return tail.removeSuffix("/join").substringBefore('/').substringBefore('?')
}
