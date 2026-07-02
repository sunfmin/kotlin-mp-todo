package com.example.todo.server.plugins

import com.example.todo.common.ApiError
import com.example.todo.server.DomainException
import com.example.todo.server.auth.AuthException
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AuthException> { call, cause ->
            val status = when (cause.kind) {
                AuthException.Kind.INVALID_CODE -> HttpStatusCode.Unauthorized
                AuthException.Kind.UNAUTHORIZED -> HttpStatusCode.Unauthorized
                AuthException.Kind.RATE_LIMITED -> HttpStatusCode.TooManyRequests
                AuthException.Kind.BAD_REQUEST -> HttpStatusCode.BadRequest
            }
            call.respond(status, ApiError(cause.message ?: "Request failed"))
        }
        exception<DomainException> { call, cause ->
            call.respond(cause.status, ApiError(cause.message ?: "Request failed"))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request."))
        }
    }
}
