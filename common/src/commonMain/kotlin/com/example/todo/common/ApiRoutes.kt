package com.example.todo.common

/** API paths shared between server and clients so the contract has a single source of truth. */
object ApiRoutes {
    const val HEALTH = "/health"

    // Auth (slice 2)
    const val AUTH_OTP_REQUEST = "/auth/otp/request"
    const val AUTH_OTP_VERIFY = "/auth/otp/verify"
    const val AUTH_TOKEN_REFRESH = "/auth/token/refresh"
    const val AUTH_SIGNOUT = "/auth/signout"
    const val ME = "/me"

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
}
