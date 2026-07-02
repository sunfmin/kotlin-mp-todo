package com.example.todo.common

import kotlinx.serialization.Serializable

/**
 * Slice 1 walking-skeleton contract: the server's health/ping response.
 * Later slices add the real API DTOs (auth, lists, todos, membership) alongside this.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    /** True when the server verified database connectivity while handling the request. */
    val databaseConnected: Boolean,
)

/** API paths shared between server and clients so the contract has a single source of truth. */
object ApiRoutes {
    const val HEALTH = "/health"
}
