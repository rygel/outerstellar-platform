package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.JooqApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JooqAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JooqContactRepository
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.JooqOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JooqPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NotificationService
import javax.sql.DataSource
import org.http4k.core.HttpHandler
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL

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
            AppConfig(
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

        // --- Shared repositories (lazy, created once per JVM) ---

        val renderer by lazy { createRenderer() }
        val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
        val userRepository by lazy { JooqUserRepository(testDsl) }
        val messageRepository by lazy { JooqMessageRepository(testDsl) }
        val contactRepository by lazy { JooqContactRepository(testDsl) }
        val sessionRepository by lazy { JooqSessionRepository(testDsl) }
        val apiKeyRepository by lazy { JooqApiKeyRepository(testDsl) }
        val auditRepository by lazy { JooqAuditRepository(testDsl) }
        val notificationRepository by lazy { JooqNotificationRepository(testDsl) }
        val passwordResetRepository by lazy { JooqPasswordResetRepository(testDsl) }
        val oauthRepository by lazy { JooqOAuthRepository(testDsl) }

        /**
         * Builds the standard test app. Most tests should call this with no arguments. Override [config] for tests that
         * need custom AppConfig (e.g. CSRF enabled). Override [securityService] for tests that need a custom
         * SecurityService setup. Override [notificationService] for tests that need notification support.
         */
        fun buildApp(
            config: AppConfig = testConfig,
            userRepository: UserRepository = this.userRepository,
            messageCache: MessageCache = StubMessageCache(),
            securityService: SecurityService =
                SecurityService(
                    userRepository,
                    encoder,
                    sessionRepository = sessionRepository,
                    apiKeyRepository = apiKeyRepository,
                    resetRepository = passwordResetRepository,
                    auditRepository = auditRepository,
                ),
            contactService: ContactService? = null,
            notificationService: NotificationService? = null,
            deviceTokenRepository: io.github.rygel.outerstellar.platform.security.DeviceTokenRepository? = null,
        ): HttpHandler {
            val outbox = StubOutboxRepository()
            val txManager = StubTransactionManager()
            val messageService = MessageService(messageRepository, outbox, txManager, messageCache)
            val resolvedContactService =
                contactService
                    ?: ContactService(
                        contactRepository,
                        transactionManager = txManager,
                        auditRepository = auditRepository,
                    )
            val pageFactory = WebPageFactory(messageRepository, messageService, resolvedContactService, securityService)

            return app(
                    messageService,
                    resolvedContactService,
                    outbox,
                    messageCache,
                    renderer,
                    pageFactory,
                    config,
                    securityService,
                    userRepository,
                    deviceTokenRepository = deviceTokenRepository,
                    notificationService = notificationService,
                )
                .http!!
        }
    }
}
