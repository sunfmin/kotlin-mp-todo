package com.example.todo.server.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource

/**
 * Owns the single authoritative datastore connection (ADR-0002, ADR-0007).
 * Runs Flyway migrations on connect and exposes a health probe.
 */
object DatabaseFactory {
    @Volatile
    private var dataSource: DataSource? = null

    fun connect(jdbcUrl: String, user: String, password: String) {
        val ds = HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = user
                this.password = password
                driverClassName = "org.postgresql.Driver"
                maximumPoolSize = 6
                isAutoCommit = false
                transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            }
        )
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        Database.connect(ds)
        dataSource = ds
    }

    /** True when a trivial query round-trips to the database. */
    fun isHealthy(): Boolean = try {
        transaction { exec("SELECT 1") {} }
        true
    } catch (_: Exception) {
        false
    }
}
