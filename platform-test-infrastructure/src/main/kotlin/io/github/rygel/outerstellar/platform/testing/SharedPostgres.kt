package io.github.rygel.outerstellar.platform.testing

import java.sql.DriverManager
import org.testcontainers.containers.PostgreSQLContainer

object SharedPostgres {
    private val lock = Any()

    private val container: PostgreSQLContainer<Nothing> by lazy {
        PostgreSQLContainer<Nothing>("postgres:18").apply {
            withDatabaseName("outerstellar_base")
            withUsername("outerstellar")
            withPassword("outerstellar")
            withReuse(true)
            start()
        }
    }

    fun createDatabase(name: String): TestDatabase {
        val dbName = "test_${sanitizeDbName(name)}"
        synchronized(lock) {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().use { stmt -> stmt.execute("CREATE DATABASE \"$dbName\"") }
            }
        }
        val jdbcUrl = container.jdbcUrl.replaceAfterLast('/', "$dbName")
        return TestDatabase(dbName, jdbcUrl, container.username, container.password)
    }

    fun dropDatabase(dbName: String) {
        synchronized(lock) {
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { conn ->
                conn.createStatement().use { stmt -> stmt.execute("DROP DATABASE IF EXISTS \"$dbName\"") }
            }
        }
    }
}

private const val MAX_DB_NAME_LENGTH = 58

fun sanitizeDbName(className: String): String =
    className.lowercase().replace(Regex("[^a-z0-9_]"), "_").take(MAX_DB_NAME_LENGTH)
