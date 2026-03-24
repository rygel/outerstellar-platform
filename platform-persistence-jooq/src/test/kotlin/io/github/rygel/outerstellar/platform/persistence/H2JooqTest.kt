package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

abstract class H2JooqTest {
    protected lateinit var dsl: DSLContext
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun setupDatabase() {
        val shared = postgresDataSource
        if (shared != null) {
            dataSource = shared
            dsl = postgresDsl!!
        } else {
            val url = "jdbc:h2:mem:${javaClass.simpleName.lowercase()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
            dataSource = createDataSource(url, "sa", "")
            migrate(dataSource)
            dsl = DSL.using(dataSource, SQLDialect.H2)
        }
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
        private val postgresDataSource: DataSource? =
            System.getProperty("test.jdbc.url")
                ?.takeIf { it.startsWith("jdbc:postgresql:") }
                ?.let { url ->
                    createDataSource(
                            url,
                            System.getProperty("test.jdbc.user", "outerstellar"),
                            System.getProperty("test.jdbc.password", "outerstellar"),
                        )
                        .also { migrate(it) }
                }

        private val postgresDsl: DSLContext? = postgresDataSource?.let { DSL.using(it, SQLDialect.POSTGRES) }
    }
}
