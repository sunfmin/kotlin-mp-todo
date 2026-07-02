package com.example.todo.server.routes

import com.example.todo.common.ApiRoutes
import com.example.todo.common.HealthResponse
import com.example.todo.server.plugins.DatabaseFactory
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

fun Route.healthRoutes() {
    get(ApiRoutes.HEALTH) {
        call.respond(
            HealthResponse(
                status = "ok",
                service = "kotlin-mp-todo-server",
                databaseConnected = DatabaseFactory.isHealthy(),
            )
        )
    }
}
