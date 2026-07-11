package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.AppleOAuthConfig
import io.github.rygel.outerstellar.platform.JwtConfig
import io.github.rygel.outerstellar.platform.RuntimeConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.di.WebPageFactories
import io.github.rygel.outerstellar.platform.di.WebRuntimeComponents
import io.github.rygel.outerstellar.platform.di.loadPersistenceBootstrap
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.User
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.AccountService
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.JwtService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SecurityConfig
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.security.TokenHashing
import io.github.rygel.outerstellar.platform.security.TotpSecretEncryption
import io.github.rygel.outerstellar.platform.security.UserAdminService
import io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.EmailService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.service.NoOpEmailService
import io.github.rygel.outerstellar.platform.service.NotificationService
import io.github.rygel.outerstellar.platform.service.OutboxProcessor
import io.github.rygel.outerstellar.platform.service.PollService
import io.github.rygel.outerstellar.platform.service.VoteService
import io.github.rygel.outerstellar.platform.testing.SharedPostgres
import io.github.rygel.outerstellar.platform.testing.sanitizeDbName
import java.util.UUID
import org.http4k.core.HttpHandler
import org.http4k.core.RequestSource
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
    val emailService: EmailService? = null,
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
            tokenPepper = "platform-web-integration-test-token-pepper",
            runtime = RuntimeConfig(hikariMaximumPoolSize = 2, hikariMinimumIdle = 0),
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
    private val platformPersistenceDelegate = lazy { loadPersistenceBootstrap().create(testConfig) }
    private val platformPersistence
        get() = platformPersistenceDelegate.value

    open val userRepository by lazy { platformPersistence.userRepository }
    val messageRepository by lazy { platformPersistence.messageRepository }
    val contactRepository by lazy { platformPersistence.contactRepository }
    val sessionRepository by lazy { platformPersistence.sessionRepository }
    val apiKeyRepository by lazy { platformPersistence.apiKeyRepository }
    val auditRepository by lazy { platformPersistence.auditRepository }
    val notificationRepository by lazy { platformPersistence.notificationRepository }
    val passwordResetRepository by lazy { platformPersistence.passwordResetRepository }
    val outboxRepository by lazy { platformPersistence.outboxRepository }
    val voteRepository by lazy { platformPersistence.voteRepository }
    val pollRepository by lazy { platformPersistence.pollRepository }
    open val oauthRepository by lazy { platformPersistence.oAuthRepository }
    val pollService by lazy { PollService(pollRepository) }

    val userAdminService by lazy { UserAdminService(userRepository, auditRepository) }
    val tokenHashing by lazy { TokenHashing(testConfig.tokenPepper) }
    val sessionSvc by lazy {
        SessionService(sessionRepository, userRepository, SecurityConfig(), tokenHashing = tokenHashing)
    }

    val apiKeyService by lazy { ApiKeyService(userRepository, apiKeyRepository, auditRepository, tokenHashing) }
    val passwordResetService by lazy {
        PasswordResetService(
            userRepository,
            encoder,
            passwordResetRepository,
            auditRepository,
            sessionRepository,
            NoOpEmailService(),
            tokenHashing = tokenHashing,
        )
    }
    val oauthService by lazy { OAuthService(userRepository, encoder, oauthRepository, auditRepository) }

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
        val token = sessionSvc.createSession(userId)
        return Triple(token, userId.toString(), username)
    }

    fun buildApp(
        config: AppConfig = testConfig,
        overrides: TestOverrides = TestOverrides(),
        extension: PlatformExtension? = null,
        defaultRequestSource: RequestSource? = RequestSource(TEST_LOOPBACK_ADDRESS),
    ): HttpHandler {
        val resolvedUserRepo = overrides.userRepository ?: this.userRepository
        val resolvedMessageCache = overrides.messageCache ?: StubMessageCache()
        val outbox = StubOutboxRepository()
        val txManager = StubTransactionManager()
        val messageService = MessageService(messageRepository, outbox, txManager, resolvedMessageCache)
        val resolvedContactService =
            overrides.contactService
                ?: ContactService(contactRepository, transactionManager = txManager, auditRepository = auditRepository)
        val resolvedPollService = overrides.pollService ?: pollService
        val notificationService = overrides.notificationService ?: NotificationService(notificationRepository)
        val emailService = overrides.emailService ?: NoOpEmailService()
        val syncWebSocket = SyncWebSocket(sessionSvc)

        val adminPageFactory = AdminPageFactory(apiKeyService, notificationService, userAdminService)
        val authPageFactory = AuthPageFactory(true)
        val errorPageFactory = ErrorPageFactory()
        val sidebarFactory = SidebarFactory()
        val settingsPageFactory = SettingsPageFactory(adminPageFactory, authPageFactory, sidebarFactory)
        val searchPageFactory = SearchPageFactory()
        val devDashboardPageFactory = DevDashboardPageFactory()
        val contactTrashListFactory = ContactTrashListFactory(resolvedContactService)
        val contactsPageFactory = ContactsPageFactory(resolvedContactService, contactTrashListFactory)
        val homePageFactory = HomePageFactory(messageService, contactTrashListFactory)
        val infraPageFactory = InfraPageFactory(messageRepository)
        val authService = buildAuthService()
        val accountService = AccountService(userRepository, encoder, sessionRepository, auditRepository)

        val persistence = buildPersistence(resolvedUserRepo, outbox, txManager, overrides)
        val security = buildSecurity(resolvedUserRepo, authService, accountService, emailService)
        val core =
            buildCore(
                messageService,
                resolvedMessageCache,
                resolvedContactService,
                outbox,
                txManager,
                syncWebSocket,
                emailService,
            )
        val pageFactories =
            WebPageFactories(
                adminPageFactory = adminPageFactory,
                authPageFactory = authPageFactory,
                errorPageFactory = errorPageFactory,
                sidebarFactory = sidebarFactory,
                settingsPageFactory = settingsPageFactory,
                searchPageFactory = searchPageFactory,
                devDashboardPageFactory = devDashboardPageFactory,
                homePageFactory = homePageFactory,
                infraPageFactory = infraPageFactory,
                contactsPageFactory = contactsPageFactory,
            )
        val web =
            buildWeb(
                pageFactories,
                resolvedPollService,
                notificationService,
                oauthService,
                resolvedUserRepo,
                syncWebSocket,
            )

        return withDefaultRequestSource(
            app(
                    config = config,
                    persistence = persistence,
                    security = security,
                    core = core,
                    web = web,
                    extension = extension,
                )
                .http!!,
            defaultRequestSource,
        )
    }

    private fun withDefaultRequestSource(handler: HttpHandler, defaultRequestSource: RequestSource?): HttpHandler {
        if (defaultRequestSource == null) return handler
        return { request -> handler(if (request.source == null) request.source(defaultRequestSource) else request) }
    }

    private fun buildAuthService(): AuthService =
        AuthService(
            userRepository,
            encoder,
            auditRepository,
            totpService = TOTPService(encoder),
            totpSecretEncryption = TotpSecretEncryption(testConfig.tokenPepper),
        )

    private fun buildPersistence(
        resolvedUserRepo: UserRepository,
        outbox: OutboxRepository,
        txManager: TransactionManager,
        overrides: TestOverrides,
    ): PlatformPersistence =
        object : PlatformPersistence {
            override val messageRepository = this@WebTest.messageRepository
            override val contactRepository = this@WebTest.contactRepository
            override val userRepository = resolvedUserRepo
            override val outboxRepository = outbox
            override val transactionManager = txManager
            override val auditRepository = this@WebTest.auditRepository
            override val passwordResetRepository = this@WebTest.passwordResetRepository
            override val apiKeyRepository = this@WebTest.apiKeyRepository
            override val oAuthRepository = this@WebTest.oauthRepository
            override val deviceTokenRepository =
                overrides.deviceTokenRepository ?: platformPersistence.deviceTokenRepository
            override val sessionRepository = this@WebTest.sessionRepository
            override val voteRepository = this@WebTest.voteRepository
            override val pollRepository = this@WebTest.pollRepository
            override val notificationRepository = this@WebTest.notificationRepository
        }

    companion object {
        private const val TEST_LOOPBACK_ADDRESS = "127.0.0.1"
    }

    private fun buildSecurity(
        resolvedUserRepo: UserRepository,
        authService: AuthService,
        accountService: AccountService,
        emailService: EmailService,
    ): SecurityComponents =
        SecurityComponents(
            jwtService =
                io.github.rygel.outerstellar.platform.security.JwtService(
                    io.github.rygel.outerstellar.platform.JwtConfig()
                ),
            asyncActivityUpdater =
                io.github.rygel.outerstellar.platform.security.AsyncActivityUpdater(resolvedUserRepo),
            authService = authService,
            accountService = accountService,
            apiKeyService = apiKeyService,
            passwordResetService =
                PasswordResetService(
                    resolvedUserRepo,
                    encoder,
                    passwordResetRepository,
                    auditRepository,
                    sessionRepository,
                    emailService,
                    tokenHashing = tokenHashing,
                ),
            oauthService = oauthService,
            authRealms = emptyList(),
            totpService = TOTPService(encoder),
            sessionService = sessionSvc,
            userAdminService = userAdminService,
        )

    private fun buildCore(
        messageService: MessageService,
        resolvedMessageCache: MessageCache,
        resolvedContactService: io.github.rygel.outerstellar.platform.service.ContactService,
        outbox: OutboxRepository,
        txManager: TransactionManager,
        eventPublisher: io.github.rygel.outerstellar.platform.service.EventPublisher,
        emailService: EmailService,
    ): CoreComponents =
        CoreComponents(
            messageService = messageService,
            messageCache = resolvedMessageCache,
            contactService = resolvedContactService,
            outboxProcessor =
                io.github.rygel.outerstellar.platform.service.OutboxProcessor(
                    outboxRepository = outbox,
                    transactionManager = txManager,
                ),
            eventPublisher = eventPublisher,
            emailService = emailService,
            pushNotificationService = io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService,
        )

    private fun buildWeb(
        pageFactories: WebPageFactories,
        resolvedPollService: io.github.rygel.outerstellar.platform.service.PollService,
        notificationService: io.github.rygel.outerstellar.platform.service.NotificationService,
        oauthService: OAuthService,
        userRepository: UserRepository,
        syncWebSocket: SyncWebSocket,
    ): WebComponents =
        WebComponents(
            runtime =
                WebRuntimeComponents(
                    templateRenderer = renderer,
                    analyticsService = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
                    syncWebSocket = syncWebSocket,
                ),
            pages = pageFactories,
            hostedAppContextFactory =
                ExtensionHostContextFactory(
                    renderer = renderer,
                    apiKeyService = apiKeyService,
                    oauthService = oauthService,
                    userRepository = userRepository,
                    analytics = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
                    notificationService = notificationService,
                ),
            voteService = VoteService(voteRepository, messageRepository),
            pollService = resolvedPollService,
            notificationService = notificationService,
        )

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
        if (platformPersistenceDelegate.isInitialized()) {
            platformPersistence.close()
        }
        testDb.drop()
    }
}
