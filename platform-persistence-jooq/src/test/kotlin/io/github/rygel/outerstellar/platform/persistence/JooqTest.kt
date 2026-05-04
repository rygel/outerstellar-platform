package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.testcontainers.containers.PostgreSQLContainer

abstract class JooqTest {
    protected lateinit var dsl: DSLContext
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun setupDatabase() {
        dataSource = sharedDataSource
        dsl = sharedDsl
    }

    @AfterEach
    fun cleanDatabase() {
        dsl.execute("DELETE FROM plt_sessions")
        dsl.execute("DELETE FROM plt_notifications")
        dsl.execute("DELETE FROM plt_device_tokens")
        dsl.execute("DELETE FROM plt_oauth_connections")
        dsl.execute("DELETE FROM plt_api_keys")
        dsl.execute("DELETE FROM plt_password_reset_tokens")
        dsl.execute("DELETE FROM plt_audit_log")
        dsl.execute("DELETE FROM plt_outbox")
        dsl.execute("DELETE FROM plt_contact_emails")
        dsl.execute("DELETE FROM plt_contact_phones")
        dsl.execute("DELETE FROM plt_contact_socials")
        dsl.execute("DELETE FROM plt_contacts")
        dsl.execute("DELETE FROM plt_messages")
        dsl.execute("DELETE FROM plt_sync_state")
        dsl.execute("DELETE FROM plt_users")
    }

    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        private val sharedDataSource: DataSource by lazy {
            createDataSource(container.jdbcUrl, container.username, container.password).also { migrate(it) }
        }

        private val sharedDsl: DSLContext by lazy { DSL.using(sharedDataSource, POSTGRES) }
    }
}
