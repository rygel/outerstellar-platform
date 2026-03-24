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
                testDsl.execute("DELETE FROM plt_sessions")
                testDsl.execute("DELETE FROM plt_notifications")
                testDsl.execute("DELETE FROM plt_device_tokens")
                testDsl.execute("DELETE FROM plt_oauth_connections")
                testDsl.execute("DELETE FROM plt_api_keys")
                testDsl.execute("DELETE FROM plt_password_reset_tokens")
                testDsl.execute("DELETE FROM plt_audit_log")
                testDsl.execute("DELETE FROM plt_outbox")
                testDsl.execute("DELETE FROM plt_contact_emails")
                testDsl.execute("DELETE FROM plt_contact_phones")
                testDsl.execute("DELETE FROM plt_contact_socials")
                testDsl.execute("DELETE FROM plt_contacts")
                testDsl.execute("DELETE FROM plt_messages")
                testDsl.execute("DELETE FROM plt_sync_state")
                testDsl.execute("DELETE FROM plt_users")
            } else {
                testDsl.execute("SET REFERENTIAL_INTEGRITY FALSE")
                testDsl.execute("TRUNCATE TABLE PLT_SESSIONS")
                testDsl.execute("TRUNCATE TABLE PLT_MESSAGES")
                testDsl.execute("TRUNCATE TABLE PLT_OUTBOX")
                testDsl.execute("TRUNCATE TABLE PLT_DEVICE_TOKENS")
                testDsl.execute("TRUNCATE TABLE PLT_OAUTH_CONNECTIONS")
                testDsl.execute("TRUNCATE TABLE PLT_API_KEYS")
                testDsl.execute("TRUNCATE TABLE PLT_PASSWORD_RESET_TOKENS")
                testDsl.execute("TRUNCATE TABLE PLT_AUDIT_LOG")
                testDsl.execute("TRUNCATE TABLE PLT_CONTACTS")
                testDsl.execute("TRUNCATE TABLE PLT_USERS")
                testDsl.execute("TRUNCATE TABLE PLT_SYNC_STATE")
                testDsl.execute("TRUNCATE TABLE PLT_NOTIFICATIONS")
                testDsl.execute("SET REFERENTIAL_INTEGRITY TRUE")
            }
        }
    }
}
