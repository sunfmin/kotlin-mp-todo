package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.common.MeResponse
import com.example.todo.common.RefreshRequest
import com.example.todo.common.RequestOtpRequest
import com.example.todo.common.VerifyOtpRequest
import com.example.todo.server.auth.AuthService
import com.example.todo.server.auth.JwtSupport
import com.example.todo.server.plugins.AUTH_JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

fun Route.authRoutes(service: AuthService) {
    post(ApiRoutes.AUTH_OTP_REQUEST) {
        val req = call.receive<RequestOtpRequest>()
        call.respond(service.requestOtp(req.email))
    }
    post(ApiRoutes.AUTH_OTP_VERIFY) {
        val req = call.receive<VerifyOtpRequest>()
        call.respond(service.verifyOtp(req.email, req.code))
    }
    post(ApiRoutes.AUTH_TOKEN_REFRESH) {
        val req = call.receive<RefreshRequest>()
        call.respond(service.refresh(req.refreshToken))
    }
    post(ApiRoutes.AUTH_SIGNOUT) {
        val req = call.receive<RefreshRequest>()
        service.signOut(req.refreshToken)
        call.respond(HttpStatusCode.NoContent)
    }
}

/** Protected identity endpoint (requires a valid bearer access token). */
fun Route.meRoutes() {
    authenticate(AUTH_JWT) {
        get(ApiRoutes.ME) {
            val principal = call.principal<JWTPrincipal>()!!
            call.respond(
                MeResponse(
                    userId = principal.subject!!,
                    email = principal.payload.getClaim(JwtSupport.CLAIM_EMAIL).asString(),
                )
            )
        }
    }
}
