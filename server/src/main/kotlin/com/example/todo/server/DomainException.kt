package com.example.todo.server

import io.ktor.http.HttpStatusCode

/**
 * Expected, client-facing failure raised by domain services (lists, todos,
 * membership, …) and mapped to an HTTP status + [com.example.todo.common.ApiError]
 * body by StatusPages. Use for authorization and validation outcomes, not bugs.
 */
class DomainException(val status: HttpStatusCode, message: String) : RuntimeException(message) {
    companion object {
        fun notFound(message: String = "Not found") = DomainException(HttpStatusCode.NotFound, message)
        fun forbidden(message: String = "You don't have access to that.") =
            DomainException(HttpStatusCode.Forbidden, message)
        fun badRequest(message: String) = DomainException(HttpStatusCode.BadRequest, message)
        fun conflict(message: String) = DomainException(HttpStatusCode.Conflict, message)
    }
}
