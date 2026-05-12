package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
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
import io.github.rygel.outerstellar.platform.security.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.UserRepository
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NotificationService
import javax.sql.DataSource
import org.http4k.core.HttpHandler
import org.jooq.DSLContext
import org.jooq.SQLDialect.POSTGRES
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer

data class TestOverrides(
    val userRepository: UserRepository? = null,
    val messageCache: MessageCache? = null,
    val contactService: ContactService? = null,
    val notificationService: NotificationService? = null,
    val deviceTokenRepository: DeviceTokenRepository? = null,
)

abstract class WebTest protected constructor() {
    companion object {
        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        private val dataSource: DataSource by lazy {
            createDataSource(container.jdbcUrl, container.username, container.password).also { migrate(it) }
        }

        val testConfig =
            AppConfig(
                port = 0,
                jdbcUrl = container.jdbcUrl,
                jdbcUser = container.username,
                jdbcPassword = container.password,
                devDashboardEnabled = true,
                csrfEnabled = false,
                corsOrigins = "*",
                appleOAuth =
                    AppleOAuthConfig(
                        enabled = true,
                        clientId = "test.client.id",
                        teamId = "test.team",
                        keyId = "test.key",
                        privateKeyPem = "test.pem",
                    ),
            )

        val testDsl: DSLContext by lazy { DSL.using(dataSource, POSTGRES) }

        fun setup() {
            // Initialization is handled by lazy properties
        }

        fun cleanup() {
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
        }

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

        fun buildApp(
            config: AppConfig = testConfig,
            securityService: SecurityService =
                SecurityService(
                    userRepository,
                    encoder,
                    sessionRepository = sessionRepository,
                    apiKeyRepository = apiKeyRepository,
                    resetRepository = passwordResetRepository,
                    auditRepository = auditRepository,
                ),
            overrides: TestOverrides = TestOverrides(),
        ): HttpHandler {
            val resolvedUserRepo = overrides.userRepository ?: this.userRepository
            val resolvedMessageCache = overrides.messageCache ?: StubMessageCache()
            val outbox = StubOutboxRepository()
            val txManager = StubTransactionManager()
            val messageService = MessageService(messageRepository, outbox, txManager, resolvedMessageCache)
            val resolvedContactService =
                overrides.contactService
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
                    resolvedMessageCache,
                    renderer,
                    pageFactory,
                    config,
                    securityService,
                    resolvedUserRepo,
                    deviceTokenRepository = overrides.deviceTokenRepository,
                    notificationService = overrides.notificationService,
                )
                .http!!
        }
    }
}
