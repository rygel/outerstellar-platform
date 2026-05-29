package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.web.StubMessageCache
import io.github.rygel.outerstellar.platform.web.WebPageFactory
import io.github.rygel.outerstellar.platform.web.bodyContains
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.hamkrest.hasStatus

class PlatformAppTest {
    @Test
    fun `can start the app and get home page`() {
        val repository = mockk<MessageRepository>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        every { userRepository.countAll() } returns 0L
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:postgresql://localhost/test", devDashboardEnabled = true)

        val persistence = buildMockPersistence(repository, userRepository)
        val security = buildMockSecurity()
        val core = buildMockCore()
        val web = buildMockWeb(repository, security.apiKeyService, security.sessionService)

        val handler = app(config = config, persistence = persistence, security = security, core = core, web = web).http
        assertNotNull(handler)

        val healthResponse = handler(Request(Method.GET, "/health"))
        assertThat(healthResponse, hasStatus(Status.OK))
        assertThat(healthResponse, bodyContains("UP"))
    }

    @Suppress("LongParameterList")
    private fun buildMockPersistence(
        repository: MessageRepository,
        userRepository: UserRepository,
    ): PlatformPersistence =
        object : PlatformPersistence {
            override val messageRepository = repository
            override val contactRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.ContactRepository>(relaxed = true)
            override val userRepository = userRepository
            override val outboxRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.OutboxRepository>(relaxed = true)
            override val transactionManager =
                mockk<io.github.rygel.outerstellar.platform.persistence.TransactionManager>(relaxed = true)
            override val auditRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.AuditRepository>(relaxed = true)
            override val passwordResetRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.PasswordResetRepository>(relaxed = true)
            override val apiKeyRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.ApiKeyRepository>(relaxed = true)
            override val oAuthRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.OAuthRepository>(relaxed = true)
            override val deviceTokenRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.DeviceTokenRepository>(relaxed = true)
            override val sessionRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.SessionRepository>(relaxed = true)
            override val voteRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.VoteRepository>(relaxed = true)
            override val pollRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.PollRepository>(relaxed = true)
            override val notificationRepository =
                mockk<io.github.rygel.outerstellar.platform.persistence.NotificationRepository>(relaxed = true)
        }

    private fun buildMockSecurity(): SecurityComponents =
        SecurityComponents(
            jwtService = mockk(relaxed = true),
            asyncActivityUpdater = mockk(relaxed = true),
            authService = mockk(relaxed = true),
            accountService = mockk(relaxed = true),
            apiKeyService = mockk(relaxed = true),
            passwordResetService = mockk(relaxed = true),
            oauthService = mockk(relaxed = true),
            authRealms = emptyList(),
            totpService = TOTPService(),
            sessionService = mockk(relaxed = true),
            userAdminService = mockk(relaxed = true),
        )

    private fun buildMockCore(): CoreComponents =
        CoreComponents(
            messageService = mockk(relaxed = true),
            messageCache = StubMessageCache(),
            contactService = mockk(relaxed = true),
            outboxProcessor = mockk(relaxed = true),
            eventPublisher = io.github.rygel.outerstellar.platform.service.NoOpEventPublisher,
            emailService = io.github.rygel.outerstellar.platform.service.ConsoleEmailService(),
            pushNotificationService = io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService,
        )

    @Suppress("LongParameterList")
    private fun buildMockWeb(
        repository: MessageRepository,
        @Suppress("UNUSED_PARAMETER") apiKeyService: ApiKeyService,
        sessionService: SessionService,
    ): WebComponents {
        val notificationService =
            mockk<io.github.rygel.outerstellar.platform.service.NotificationService>(relaxed = true)
        val voteRepo = mockk<io.github.rygel.outerstellar.platform.persistence.VoteRepository>(relaxed = true)
        val pollRepo = mockk<io.github.rygel.outerstellar.platform.persistence.PollRepository>(relaxed = true)
        return WebComponents(
            templateRenderer = createRenderer(),
            pageFactory = WebPageFactory(repository, mockk(relaxed = true), null, null),
            analyticsService = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
            emailService = io.github.rygel.outerstellar.platform.service.NoOpEmailService(),
            i18nService = io.github.rygel.outerstellar.i18n.I18nService.create("messages"),
            syncWebSocket = io.github.rygel.outerstellar.platform.web.SyncWebSocket(sessionService),
            eventPublisher = io.github.rygel.outerstellar.platform.service.NoOpEventPublisher,
            voteService = io.github.rygel.outerstellar.platform.service.VoteService(voteRepo, repository),
            pollService = io.github.rygel.outerstellar.platform.service.PollService(pollRepo),
            notificationService = notificationService,
            adminStatsService = io.github.rygel.outerstellar.platform.security.AdminStatsService(mockk(relaxed = true)),
            pluginMigrationSource = object : PluginMigrationSource {},
        )
    }
}
