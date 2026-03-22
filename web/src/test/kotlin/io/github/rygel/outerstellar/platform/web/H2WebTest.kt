package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.migrate
import javax.sql.DataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

@Suppress("UtilityClassWithPublicConstructor")
abstract class H2WebTest {
    companion object {
        private val postgresUrl: String? =
            System.getProperty("test.jdbc.url")?.takeIf { it.startsWith("jdbc:postgresql:") }

        private val jdbcUrl = postgresUrl ?: "jdbc:h2:mem:webtestdb_unique;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"

        private val jdbcUser = if (postgresUrl != null) System.getProperty("test.jdbc.user", "outerstellar") else "sa"

        private val jdbcPassword =
            if (postgresUrl != null) {
                System.getProperty("test.jdbc.password", "outerstellar")
            } else {
                ""
            }

        private val dataSource: DataSource by lazy {
            createDataSource(jdbcUrl, jdbcUser, jdbcPassword).also { migrate(it) }
        }

        val testConfig =
            io.github.rygel.outerstellar.platform.AppConfig(
                port = 0,
                jdbcUrl = jdbcUrl,
                jdbcUser = jdbcUser,
                jdbcPassword = jdbcPassword,
                devDashboardEnabled = true,
                csrfEnabled = false, // disabled in tests — covered by CsrfProtectionIntegrationTest
            )

        val testDsl: DSLContext by lazy {
            DSL.using(dataSource, if (postgresUrl != null) SQLDialect.POSTGRES else SQLDialect.H2)
        }

        fun setup() {
            // Initialization is handled by lazy properties
        }

        fun cleanup() {
            if (postgresUrl != null) {
                // PostgreSQL: delete in FK-safe order (children before parents)
                testDsl.execute("DELETE FROM sessions")
                testDsl.execute("DELETE FROM notifications")
                testDsl.execute("DELETE FROM device_tokens")
                testDsl.execute("DELETE FROM oauth_connections")
                testDsl.execute("DELETE FROM api_keys")
                testDsl.execute("DELETE FROM password_reset_tokens")
                testDsl.execute("DELETE FROM audit_log")
                testDsl.execute("DELETE FROM outbox")
                testDsl.execute("DELETE FROM contact_emails")
                testDsl.execute("DELETE FROM contact_phones")
                testDsl.execute("DELETE FROM contact_socials")
                testDsl.execute("DELETE FROM contacts")
                testDsl.execute("DELETE FROM messages")
                testDsl.execute("DELETE FROM sync_state")
                testDsl.execute("DELETE FROM users")
            } else {
                testDsl.execute("SET REFERENTIAL_INTEGRITY FALSE")
                testDsl.execute("TRUNCATE TABLE SESSIONS")
                testDsl.execute("TRUNCATE TABLE MESSAGES")
                testDsl.execute("TRUNCATE TABLE OUTBOX")
                testDsl.execute("TRUNCATE TABLE DEVICE_TOKENS")
                testDsl.execute("TRUNCATE TABLE OAUTH_CONNECTIONS")
                testDsl.execute("TRUNCATE TABLE API_KEYS")
                testDsl.execute("TRUNCATE TABLE PASSWORD_RESET_TOKENS")
                testDsl.execute("TRUNCATE TABLE AUDIT_LOG")
                testDsl.execute("TRUNCATE TABLE CONTACTS")
                testDsl.execute("TRUNCATE TABLE USERS")
                testDsl.execute("TRUNCATE TABLE SYNC_STATE")
                testDsl.execute("TRUNCATE TABLE NOTIFICATIONS")
                testDsl.execute("SET REFERENTIAL_INTEGRITY TRUE")
            }
        }
    }
}
