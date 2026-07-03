package com.example.todo.common

/** API paths shared between server and clients so the contract has a single source of truth. */
object ApiRoutes {
    const val HEALTH = "/health"

    /** Server-Sent Events stream of "List X changed" notifications (slice 8). */
    const val EVENTS = "/events"

    /** SSE event name for a List-changed notification (payload = the List id). */
    const val EVENT_LIST_CHANGED = "list-changed"

    // Auth (slice 2)
    const val AUTH_OTP_REQUEST = "/auth/otp/request"
    const val AUTH_OTP_VERIFY = "/auth/otp/verify"
    const val AUTH_TOKEN_REFRESH = "/auth/token/refresh"
    const val AUTH_SIGNOUT = "/auth/signout"
    const val ME = "/me"

    // Account lifecycle (slice 7)

    /** DELETE removes the User's account (blocked while they own shared Lists). */
    const val ACCOUNT = "/me/account"

    /** GET the shared Lists that currently block account deletion. */
    const val ACCOUNT_DELETION_BLOCKERS = "/me/account/deletion-blockers"

    // Lists (slice 3)
    const val LISTS = "/lists"

    /** Path for a single List by id. */
    fun list(id: String) = "$LISTS/$id"

    // Todos (slice 4)

    /** Path for the Todos of a List. */
    fun todos(listId: String) = "${list(listId)}/todos"

    /** Path for a single Todo within a List. */
    fun todo(listId: String, todoId: String) = "${todos(listId)}/$todoId"

    /** Path to reorder a Todo within its List. */
    fun todoReorder(listId: String, todoId: String) = "${todo(listId, todoId)}/reorder"

    /** Path to assign/unassign a Todo (slice 6). */
    fun todoAssignee(listId: String, todoId: String) = "${todo(listId, todoId)}/assignee"

    // Membership & sharing (slice 5)

    /** Members of a List. */
    fun members(listId: String) = "${list(listId)}/members"

    /** A single member of a List (for remove/leave). */
    fun member(listId: String, userId: String) = "${members(listId)}/$userId"

    /** The List's single Invite Link (GET current, POST regenerate, DELETE revoke). */
    fun inviteLink(listId: String) = "${list(listId)}/invite-link"

    /** Transfer ownership of a List to another member (slice 7). */
    fun listTransfer(listId: String) = "${list(listId)}/transfer"

    /** Base path for following an Invite Link by its token. */
    const val INVITE = "/invite"

    /** Preview the List an Invite Link points to (name only). */
    fun invitePreview(token: String) = "$INVITE/$token"

    /** Join the List an active Invite Link points to, as an EDITOR. */
    fun inviteJoin(token: String) = "$INVITE/$token/join"
}
