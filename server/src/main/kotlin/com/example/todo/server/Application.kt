package com.example.todo.server

import com.example.todo.server.plugins.DatabaseFactory
import com.example.todo.server.plugins.configureSerialization
import com.example.todo.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module).start(wait = true)
}

/**
 * The application module. Reads database configuration from the Ktor config
 * (keys `db.jdbcUrl`, `db.user`, `db.password`), so tests can point it at a
 * Testcontainers Postgres instead of a real database.
 */
fun Application.module() {
    val jdbcUrl = configString("db.jdbcUrl") ?: System.getenv("DB_JDBC_URL")
        ?: "jdbc:postgresql://localhost:5432/todo"
    val user = configString("db.user") ?: System.getenv("DB_USER") ?: "todo"
    val password = configString("db.password") ?: System.getenv("DB_PASSWORD") ?: "todo"

    DatabaseFactory.connect(jdbcUrl, user, password)

    configureSerialization()
    routing {
        healthRoutes()
    }
}

private fun Application.configString(path: String): String? =
    environment.config.propertyOrNull(path)?.getString()
