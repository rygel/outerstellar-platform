package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PlatformPersistence
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.di.WebPageFactories
import io.github.rygel.outerstellar.platform.di.WebRuntimeComponents
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.web.ExtensionHostContextFactory
import io.github.rygel.outerstellar.platform.web.StubMessageCache
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
        val web =
            buildMockWeb(
                repository,
                userRepository,
                security.apiKeyService,
                security.oauthService,
                security.sessionService,
            )

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
        userRepository: UserRepository,
        apiKeyService: ApiKeyService,
        oauthService: OAuthService,
        sessionService: SessionService,
    ): WebComponents {
        val messageService = mockk<io.github.rygel.outerstellar.platform.service.MessageService>(relaxed = true)
        val notificationService =
            mockk<io.github.rygel.outerstellar.platform.service.NotificationService>(relaxed = true)
        val voteRepo = mockk<io.github.rygel.outerstellar.platform.persistence.VoteRepository>(relaxed = true)
        val pollRepo = mockk<io.github.rygel.outerstellar.platform.persistence.PollRepository>(relaxed = true)
        val adminPageFactory =
            io.github.rygel.outerstellar.platform.web.AdminPageFactory(null, notificationService, null)
        val authPageFactory = io.github.rygel.outerstellar.platform.web.AuthPageFactory()
        val errorPageFactory = io.github.rygel.outerstellar.platform.web.ErrorPageFactory()
        val sidebarFactory = io.github.rygel.outerstellar.platform.web.SidebarFactory()
        val settingsPageFactory =
            io.github.rygel.outerstellar.platform.web.SettingsPageFactory(
                adminPageFactory,
                authPageFactory,
                sidebarFactory,
            )
        val searchPageFactory = io.github.rygel.outerstellar.platform.web.SearchPageFactory()
        val devDashboardPageFactory = io.github.rygel.outerstellar.platform.web.DevDashboardPageFactory()
        val contactTrashListFactory = io.github.rygel.outerstellar.platform.web.ContactTrashListFactory(null)
        val contactsPageFactory =
            io.github.rygel.outerstellar.platform.web.ContactsPageFactory(null, contactTrashListFactory)
        val homePageFactory =
            io.github.rygel.outerstellar.platform.web.HomePageFactory(messageService, contactTrashListFactory)
        val infraPageFactory = io.github.rygel.outerstellar.platform.web.InfraPageFactory(repository)
        val renderer = createRenderer()
        return WebComponents(
            runtime =
                WebRuntimeComponents(
                    templateRenderer = renderer,
                    analyticsService = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
                    syncWebSocket = io.github.rygel.outerstellar.platform.web.SyncWebSocket(sessionService),
                ),
            pages =
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
                ),
            hostedAppContextFactory =
                ExtensionHostContextFactory(
                    renderer = renderer,
                    apiKeyService = apiKeyService,
                    oauthService = oauthService,
                    userRepository = userRepository,
                    analytics = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
                    notificationService = notificationService,
                ),
            voteService = io.github.rygel.outerstellar.platform.service.VoteService(voteRepo, repository),
            pollService = io.github.rygel.outerstellar.platform.service.PollService(pollRepo),
            notificationService = notificationService,
        )
    }
}
