package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
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
            handle.execute("DELETE FROM plt_sessions")
            handle.execute("DELETE FROM plt_notifications")
            handle.execute("DELETE FROM plt_device_tokens")
            handle.execute("DELETE FROM plt_oauth_connections")
            handle.execute("DELETE FROM plt_api_keys")
            handle.execute("DELETE FROM plt_password_reset_tokens")
            handle.execute("DELETE FROM plt_audit_log")
            handle.execute("DELETE FROM plt_outbox")
            handle.execute("DELETE FROM plt_contact_emails")
            handle.execute("DELETE FROM plt_contact_phones")
            handle.execute("DELETE FROM plt_contact_socials")
            handle.execute("DELETE FROM plt_contacts")
            handle.execute("DELETE FROM plt_messages")
            handle.execute("DELETE FROM plt_sync_state")
            handle.execute("DELETE FROM plt_users")
        }
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
