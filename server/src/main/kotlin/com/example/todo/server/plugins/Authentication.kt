package com.example.todo.server.plugins

import com.example.todo.server.auth.JwtSupport
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt

const val AUTH_JWT = "auth-jwt"

fun Application.configureAuthentication(jwt: JwtSupport) {
    install(Authentication) {
        jwt(AUTH_JWT) {
            realm = "kotlin-mp-todo"
            verifier(jwt.verifier)
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
