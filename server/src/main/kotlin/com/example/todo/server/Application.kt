package com.example.todo.server

import com.example.todo.server.plugins.DatabaseFactory
import com.example.todo.server.plugins.configureSerialization
import com.example.todo.server.routes.healthRoutes
import io.ktor.server.application.Application
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
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

    // Dev CORS so the Compose HTML web client (served from a different origin/port)
    // can call the API. Tighten to specific hosts before production.
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
    }

    configureSerialization()
    routing {
        healthRoutes()
    }
}

private fun Application.configString(path: String): String? =
    environment.config.propertyOrNull(path)?.getString()
