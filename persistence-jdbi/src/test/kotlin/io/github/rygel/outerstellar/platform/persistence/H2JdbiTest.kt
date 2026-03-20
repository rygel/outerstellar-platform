package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class H2JdbiTest {

    protected lateinit var jdbi: Jdbi

    @BeforeEach
    fun setupDatabase() {
        val jdbcUrl =
            "jdbc:h2:mem:${javaClass.simpleName.lowercase()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        val dataSource = createDataSource(jdbcUrl, "sa", "")
        migrate(dataSource)
        jdbi = Jdbi.create(dataSource)
    }

    @AfterEach
    fun cleanDatabase() {
        jdbi.useHandle<Exception> { handle ->
            handle.execute("DELETE FROM sessions")
            handle.execute("DELETE FROM notifications")
            handle.execute("DELETE FROM device_tokens")
            handle.execute("DELETE FROM oauth_connections")
            handle.execute("DELETE FROM api_keys")
            handle.execute("DELETE FROM password_reset_tokens")
            handle.execute("DELETE FROM audit_log")
            handle.execute("DELETE FROM outbox")
            handle.execute("DELETE FROM contact_emails")
            handle.execute("DELETE FROM contact_phones")
            handle.execute("DELETE FROM contact_socials")
            handle.execute("DELETE FROM contacts")
            handle.execute("DELETE FROM messages")
            handle.execute("DELETE FROM sync_state")
            handle.execute("DELETE FROM users")
        }
    }
}
