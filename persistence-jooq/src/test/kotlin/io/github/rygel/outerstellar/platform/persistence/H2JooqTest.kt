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
        dsl.execute("DELETE FROM sessions")
        dsl.execute("DELETE FROM notifications")
        dsl.execute("DELETE FROM device_tokens")
        dsl.execute("DELETE FROM oauth_connections")
        dsl.execute("DELETE FROM api_keys")
        dsl.execute("DELETE FROM password_reset_tokens")
        dsl.execute("DELETE FROM audit_log")
        dsl.execute("DELETE FROM outbox")
        dsl.execute("DELETE FROM contact_emails")
        dsl.execute("DELETE FROM contact_phones")
        dsl.execute("DELETE FROM contact_socials")
        dsl.execute("DELETE FROM contacts")
        dsl.execute("DELETE FROM messages")
        dsl.execute("DELETE FROM sync_state")
        dsl.execute("DELETE FROM users")
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
