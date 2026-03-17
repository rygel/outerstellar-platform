package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.infra.createDataSource
import dev.outerstellar.starter.infra.migrate
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
        val jdbcUrl =
            "jdbc:h2:mem:${javaClass.simpleName.lowercase()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1"
        dataSource = createDataSource(jdbcUrl, "sa", "")
        migrate(dataSource)
        dsl = DSL.using(dataSource, SQLDialect.H2)
    }

    @AfterEach
    fun cleanDatabase() {
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
}
