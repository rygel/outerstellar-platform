package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiApiKeyRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiContactRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiNotificationRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiOAuthRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPasswordResetRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiPollRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JdbiUserRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.PollService
import javax.sql.DataSource
import org.http4k.core.HttpHandler
import org.jdbi.v3.core.Jdbi
import org.testcontainers.containers.PostgreSQLContainer

data class TestOverrides(
    val userRepository: UserRepository? = null,
    val messageCache: MessageCache? = null,
    val contactService: ContactService? = null,
    val notificationService: NotificationService? = null,
    val deviceTokenRepository: DeviceTokenRepository? = null,
    val pollService: PollService? = null,
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

        val testJdbi: Jdbi by lazy { Jdbi.create(dataSource) }

        fun setup() {}

        fun cleanup() {
            testJdbi.useHandle<Exception> { handle ->
                handle.execute("DELETE FROM plt_sessions")
                handle.execute("DELETE FROM plt_notifications")
                handle.execute("DELETE FROM plt_device_tokens")
                handle.execute("DELETE FROM plt_oauth_connections")
                handle.execute("DELETE FROM plt_api_keys")
                handle.execute("DELETE FROM plt_password_reset_tokens")
                handle.execute("DELETE FROM plt_audit_log")
                handle.execute("DELETE FROM plt_outbox")
                handle.execute("DELETE FROM plt_contact_emails")
                handle.execute("DELETE FROM plt_contact_phones")
                handle.execute("DELETE FROM plt_contact_socials")
                handle.execute("DELETE FROM plt_contacts")
                handle.execute("DELETE FROM plt_messages")
                handle.execute("DELETE FROM plt_poll_votes")
                handle.execute("DELETE FROM plt_poll_options")
                handle.execute("DELETE FROM plt_polls")
                handle.execute("DELETE FROM plt_sync_state")
                handle.execute("DELETE FROM plt_users")
            }
        }

        val renderer by lazy { createRenderer() }
        val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
        val userRepository by lazy { JdbiUserRepository(testJdbi) }
        val messageRepository by lazy { JdbiMessageRepository(testJdbi) }
        val contactRepository by lazy { JdbiContactRepository(testJdbi) }
        val sessionRepository by lazy { JdbiSessionRepository(testJdbi) }
        val apiKeyRepository by lazy { JdbiApiKeyRepository(testJdbi) }
        val auditRepository by lazy { JdbiAuditRepository(testJdbi) }
        val notificationRepository by lazy { JdbiNotificationRepository(testJdbi) }
        val passwordResetRepository by lazy { JdbiPasswordResetRepository(testJdbi) }
        val oauthRepository by lazy { JdbiOAuthRepository(testJdbi) }
        val pollRepository by lazy { JdbiPollRepository(testJdbi) }
        val pollService by lazy { PollService(pollRepository) }

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
            val pageFactory =
                WebPageFactory(
                    messageRepository,
                    messageService,
                    resolvedContactService,
                    securityService,
                    appleOAuthEnabled = true,
                )

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
                    pollService = overrides.pollService ?: pollService,
                )
                .http!!
        }
    }
}
