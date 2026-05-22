package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
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
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.util.UUID
import org.http4k.core.HttpHandler
import org.jdbi.v3.core.Jdbi
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.TestInstance

data class TestOverrides(
    val userRepository: UserRepository? = null,
    val messageCache: MessageCache? = null,
    val contactService: ContactService? = null,
    val notificationService: NotificationService? = null,
    val deviceTokenRepository: DeviceTokenRepository? = null,
    val pollService: PollService? = null,
)

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class WebTest {
    private val testDb = SharedPostgres.createDatabase(sanitizeDbName(this::class.simpleName!!))

    val testConfig by lazy {
        AppConfig(
            port = 0,
            jdbcUrl = testDb.jdbcUrl,
            jdbcUser = testDb.jdbcUser,
            jdbcPassword = testDb.jdbcPassword,
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
    }

    val testJdbi: Jdbi by lazy { testDb.jdbi }
    val renderer by lazy { createRenderer() }
    val encoder by lazy { BCryptPasswordEncoder(logRounds = 4) }
    val testPasswordHash by lazy { encoder.encode("Test@12345678") }

    open val userRepository by lazy { JdbiUserRepository(testJdbi) }
    val messageRepository by lazy { JdbiMessageRepository(testJdbi) }
    val contactRepository by lazy { JdbiContactRepository(testJdbi) }
    val sessionRepository by lazy { JdbiSessionRepository(testJdbi) }
    val apiKeyRepository by lazy { JdbiApiKeyRepository(testJdbi) }
    val auditRepository by lazy { JdbiAuditRepository(testJdbi) }
    val notificationRepository by lazy { JdbiNotificationRepository(testJdbi) }
    val passwordResetRepository by lazy { JdbiPasswordResetRepository(testJdbi) }
    open val oauthRepository by lazy { JdbiOAuthRepository(testJdbi) }
    val pollRepository by lazy { JdbiPollRepository(testJdbi) }
    val pollService by lazy { PollService(pollRepository) }

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
        username: String = "testuser_" + UUID.randomUUID().toString().take(8),
        passwordHash: String = testPasswordHash,
        role: String = "USER",
    ): Triple<String, String, String> {
        val userId = UUID.randomUUID()
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
                ?: ContactService(contactRepository, transactionManager = txManager, auditRepository = auditRepository)
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

    private val tablesToDelete =
        listOf(
            "plt_sessions",
            "plt_notifications",
            "plt_device_tokens",
            "plt_oauth_connections",
            "plt_api_keys",
            "plt_password_reset_tokens",
            "plt_audit_log",
            "plt_outbox",
            "plt_contact_emails",
            "plt_contact_phones",
            "plt_contact_socials",
            "plt_contacts",
            "plt_messages",
            "plt_poll_votes",
            "plt_poll_options",
            "plt_polls",
            "plt_sync_state",
            "plt_users",
        )

    @AfterEach
    fun cleanDatabase() {
        testJdbi.useHandle<Exception> { handle ->
            tablesToDelete.forEach { table -> handle.execute("DELETE FROM $table") }
        }
    }

    @AfterAll
    fun tearDown() {
        testDb.drop()
    }
}
