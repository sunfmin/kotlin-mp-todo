package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.common.CreateListRequest
import com.example.todo.common.RenameListRequest
import com.example.todo.server.lists.ListService
import com.example.todo.server.plugins.AUTH_JWT
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** List CRUD (slice 3). All routes require auth and authorize against ownership. */
fun Route.listRoutes(lists: ListService) {
    authenticate(AUTH_JWT) {
        route(ApiRoutes.LISTS) {
            get {
                call.respond(lists.listFor(call.userId()))
            }
            post {
                val req = call.receive<CreateListRequest>()
                call.respond(HttpStatusCode.Created, lists.create(call.userId(), req.name))
            }
            route("/{listId}") {
                get {
                    call.respond(lists.get(call.userId(), call.uuidParam("listId")))
                }
                put {
                    val req = call.receive<RenameListRequest>()
                    call.respond(lists.rename(call.userId(), call.uuidParam("listId"), req.name))
                }
                delete {
                    lists.delete(call.userId(), call.uuidParam("listId"))
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
    }
}
