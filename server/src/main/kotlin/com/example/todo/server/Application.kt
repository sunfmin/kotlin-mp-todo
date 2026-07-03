package com.example.todo.server

import com.example.todo.server.auth.AuthConfig
import com.example.todo.server.auth.AuthService
import com.example.todo.server.auth.JwtSupport
import com.example.todo.server.lists.ListService
import com.example.todo.server.membership.MembershipService
import com.example.todo.server.todos.TodoService
import com.example.todo.server.plugins.DatabaseFactory
import com.example.todo.server.plugins.configureAuthentication
import com.example.todo.server.plugins.configureSerialization
import com.example.todo.server.plugins.configureStatusPages
import com.example.todo.server.routes.authRoutes
import com.example.todo.server.routes.healthRoutes
import com.example.todo.server.routes.listRoutes
import com.example.todo.server.routes.meRoutes
import com.example.todo.server.routes.membershipRoutes
import com.example.todo.server.routes.todoRoutes
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

    val authConfig = AuthConfig(
        jwtSecret = configString("auth.jwtSecret") ?: System.getenv("AUTH_JWT_SECRET")
            ?: "dev-insecure-secret-change-me",
        otpReturnInResponse = (configString("auth.otpReturnInResponse")
            ?: System.getenv("AUTH_OTP_IN_RESPONSE"))?.toBoolean() ?: false,
    )
    val jwt = JwtSupport(authConfig)
    val authService = AuthService(authConfig, jwt)
    val listService = ListService()
    val todoService = TodoService()
    val membershipService = MembershipService()

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
    configureStatusPages()
    configureAuthentication(jwt)
    routing {
        healthRoutes()
        authRoutes(authService)
        meRoutes()
        listRoutes(listService)
        todoRoutes(todoService)
        membershipRoutes(membershipService)
    }
}

private fun Application.configString(path: String): String? =
    environment.config.propertyOrNull(path)?.getString()
