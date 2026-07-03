package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.common.AssignTodoRequest
import com.example.todo.common.CreateTodoRequest
import com.example.todo.common.ReorderTodoRequest
import com.example.todo.common.UpdateTodoRequest
import com.example.todo.server.plugins.AUTH_JWT
import com.example.todo.server.todos.TodoService
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route

/** Todo CRUD + reorder within a List (slice 4). All routes require auth. */
fun Route.todoRoutes(todos: TodoService) {
    authenticate(AUTH_JWT) {
        route(ApiRoutes.todos("{listId}")) {
            get {
                call.respond(todos.listForList(call.userId(), call.uuidParam("listId")))
            }
            post {
                val req = call.receive<CreateTodoRequest>()
                call.respond(
                    HttpStatusCode.Created,
                    todos.create(call.userId(), call.uuidParam("listId"), req),
                )
            }
            route("/{todoId}") {
                get {
                    call.respond(
                        todos.get(call.userId(), call.uuidParam("listId"), call.uuidParam("todoId")),
                    )
                }
                put {
                    val req = call.receive<UpdateTodoRequest>()
                    call.respond(
                        todos.update(call.userId(), call.uuidParam("listId"), call.uuidParam("todoId"), req),
                    )
                }
                delete {
                    todos.delete(call.userId(), call.uuidParam("listId"), call.uuidParam("todoId"))
                    call.respond(HttpStatusCode.NoContent)
                }
                patch("/reorder") {
                    val req = call.receive<ReorderTodoRequest>()
                    call.respond(
                        todos.reorder(call.userId(), call.uuidParam("listId"), call.uuidParam("todoId"), req),
                    )
                }
                put("/assignee") {
                    val req = call.receive<AssignTodoRequest>()
                    call.respond(
                        todos.assign(call.userId(), call.uuidParam("listId"), call.uuidParam("todoId"), req.assigneeUserId),
                    )
                }
            }
        }
    }
}
