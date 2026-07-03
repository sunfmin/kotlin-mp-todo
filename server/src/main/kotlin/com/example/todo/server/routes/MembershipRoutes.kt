package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateInviteLinkRequest
import com.example.todo.common.TransferOwnershipRequest
import com.example.todo.server.membership.MembershipService
import com.example.todo.server.plugins.AUTH_JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Sharing & membership routes (slice 5). Member list + member removal/leave and
 * the Invite Link lifecycle live under a List; the join/preview endpoints hang
 * off the opaque token. All require auth; owner-vs-member is enforced in the
 * service via [com.example.todo.server.membership.Members].
 */
fun Route.membershipRoutes(membership: MembershipService) {
    authenticate(AUTH_JWT) {
        route("${ApiRoutes.LISTS}/{listId}/members") {
            get {
                call.respond(membership.members(call.userId(), call.uuidParam("listId")))
            }
            delete("/{userId}") {
                membership.removeMember(
                    call.userId(),
                    call.uuidParam("listId"),
                    call.uuidParam("userId"),
                )
                call.respond(HttpStatusCode.NoContent)
            }
        }

        route("${ApiRoutes.LISTS}/{listId}/invite-link") {
            get {
                val link = membership.getInviteLink(call.userId(), call.uuidParam("listId"))
                    ?: throw com.example.todo.server.DomainException.notFound("No active invite link.")
                call.respond(link)
            }
            post {
                val req = call.receive<CreateInviteLinkRequest>()
                call.respond(membership.generateInviteLink(call.userId(), call.uuidParam("listId"), req.expiresAt))
            }
            delete {
                membership.revokeInviteLink(call.userId(), call.uuidParam("listId"))
                call.respond(HttpStatusCode.NoContent)
            }
        }

        post("${ApiRoutes.LISTS}/{listId}/transfer") {
            val req = call.receive<TransferOwnershipRequest>()
            call.respond(membership.transferOwnership(call.userId(), call.uuidParam("listId"), req.newOwnerUserId))
        }

        route("${ApiRoutes.INVITE}/{token}") {
            get {
                call.respond(membership.preview(call.uuidParam("token")))
            }
            post("/join") {
                call.respond(membership.join(call.userId(), call.uuidParam("token")))
            }
        }
    }
}
