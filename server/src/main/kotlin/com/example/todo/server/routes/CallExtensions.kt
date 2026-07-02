package com.example.todo.server.routes

import com.example.todo.server.DomainException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import java.util.UUID

/** The authenticated User's id, from the verified bearer token's subject. */
fun ApplicationCall.userId(): UUID =
    UUID.fromString(principal<JWTPrincipal>()!!.subject!!)

/** A required UUID path parameter, or a 400 if it is missing/malformed. */
fun ApplicationCall.uuidParam(name: String): UUID {
    val raw = parameters[name] ?: throw DomainException(HttpStatusCode.BadRequest, "Missing $name.")
    return try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw DomainException.notFound("Not found.")
    }
}
