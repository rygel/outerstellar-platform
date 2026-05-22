package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createDataSource
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.infra.migrate
import io.github.rygel.outerstellar.platform.persistence.CleanupTables
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
import org.junit.jupiter.api.AfterEach
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

    @AfterEach
    fun resetState() {
        cleanup()
    }

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

        fun cleanup() {
            testJdbi.useHandle<Exception> { handle ->
                CleanupTables.ALL.forEach { table -> handle.execute("DELETE FROM $table") }
            }
        }

        val renderer by lazy { createRenderer() }
        val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
        val testPasswordHash by lazy { encoder.encode("Test@12345678") }

        fun createSecurityService(userRepository: UserRepository = this.userRepository): SecurityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        fun withAuthenticatedUser(
            username: String = "testuser_" + java.util.UUID.randomUUID().toString().take(8),
            passwordHash: String = testPasswordHash,
            role: String = "USER",
        ): Triple<String, String, String> {
            val userId = java.util.UUID.randomUUID()
            val user =
                io.github.rygel.outerstellar.platform.model.User(
                    id = userId,
                    username = username,
                    email = "$username@test.com",
                    passwordHash = passwordHash,
                    role = io.github.rygel.outerstellar.platform.model.UserRole.valueOf(role),
                )
            userRepository.save(user)
            val token = createSecurityService().createSession(userId)
            return Triple(token, userId.toString(), username)
        }

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
            securityService: SecurityService = createSecurityService(),
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
