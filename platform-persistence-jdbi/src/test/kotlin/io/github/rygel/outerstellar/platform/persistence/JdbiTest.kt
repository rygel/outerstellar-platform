package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import java.util.UUID
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer

abstract class JdbiTest {
    protected lateinit var jdbi: Jdbi

    @BeforeEach
    fun setupDatabase() {
        jdbi = sharedJdbi
    }

    @AfterEach
    fun cleanDatabase() {
        jdbi.useHandle<Exception> { handle ->
            CleanupTables.ALL.forEach { table -> handle.execute("DELETE FROM $table") }
        }
    }

    protected fun createUser(
        username: String = "user_${UUID.randomUUID().toString().take(6)}",
        role: UserRole = UserRole.USER,
    ): UUID {
        val id = UUID.randomUUID()
        JdbiUserRepository(jdbi)
            .save(
                User(
                    id = id,
                    username = username,
                    email = "${id.toString().take(6)}@example.com",
                    passwordHash = "hash",
                    role = role,
                )
            )
        return id
    }

    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        private val sharedJdbi: Jdbi by lazy {
            val dataSource = createDataSource(container.jdbcUrl, container.username, container.password)
            migrate(dataSource)
            Jdbi.create(dataSource)
        }
    }
}
