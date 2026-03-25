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

    @BeforeEach
    fun setupDatabase() {
        dsl = sharedDsl
    }

    @AfterEach
    fun cleanDatabase() {
        if (isPostgres) {
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
        } else {
            dsl.execute("SET REFERENTIAL_INTEGRITY FALSE")
            dsl.execute("TRUNCATE TABLE PLT_SESSIONS")
            dsl.execute("TRUNCATE TABLE PLT_NOTIFICATIONS")
            dsl.execute("TRUNCATE TABLE PLT_DEVICE_TOKENS")
            dsl.execute("TRUNCATE TABLE PLT_OAUTH_CONNECTIONS")
            dsl.execute("TRUNCATE TABLE PLT_API_KEYS")
            dsl.execute("TRUNCATE TABLE PLT_PASSWORD_RESET_TOKENS")
            dsl.execute("TRUNCATE TABLE PLT_AUDIT_LOG")
            dsl.execute("TRUNCATE TABLE PLT_OUTBOX")
            dsl.execute("TRUNCATE TABLE PLT_CONTACT_EMAILS")
            dsl.execute("TRUNCATE TABLE PLT_CONTACT_PHONES")
            dsl.execute("TRUNCATE TABLE PLT_CONTACT_SOCIALS")
            dsl.execute("TRUNCATE TABLE PLT_CONTACTS")
            dsl.execute("TRUNCATE TABLE PLT_MESSAGES")
            dsl.execute("TRUNCATE TABLE PLT_SYNC_STATE")
            dsl.execute("TRUNCATE TABLE PLT_USERS")
            dsl.execute("SET REFERENTIAL_INTEGRITY TRUE")
        }
    }

    companion object {
        private val isPostgres: Boolean = System.getProperty("test.jdbc.url")?.startsWith("jdbc:postgresql:") == true

        private val sharedDataSource: DataSource by lazy {
            if (isPostgres) {
                    createDataSource(
                        System.getProperty("test.jdbc.url")!!,
                        System.getProperty("test.jdbc.user", "outerstellar"),
                        System.getProperty("test.jdbc.password", "outerstellar"),
                    )
                } else {
                    createDataSource("jdbc:h2:mem:jooqtestdb_shared;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "")
                }
                .also { migrate(it) }
        }

        private val sharedDsl: DSLContext by lazy {
            DSL.using(sharedDataSource, if (isPostgres) SQLDialect.POSTGRES else SQLDialect.H2)
        }
    }
}
