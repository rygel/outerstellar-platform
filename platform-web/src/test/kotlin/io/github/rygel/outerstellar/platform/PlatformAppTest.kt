package io.github.rygel.outerstellar.platform

import com.natpryce.hamkrest.assertion.assertThat
import io.github.rygel.outerstellar.platform.di.CoreComponents
import io.github.rygel.outerstellar.platform.di.PersistenceComponents
import io.github.rygel.outerstellar.platform.di.WebComponents
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.UserRepository
import io.github.rygel.outerstellar.platform.security.AccountService
import io.github.rygel.outerstellar.platform.security.ApiKeyService
import io.github.rygel.outerstellar.platform.security.AuthService
import io.github.rygel.outerstellar.platform.security.OAuthService
import io.github.rygel.outerstellar.platform.security.PasswordResetService
import io.github.rygel.outerstellar.platform.security.SecurityComponents
import io.github.rygel.outerstellar.platform.security.SessionService
import io.github.rygel.outerstellar.platform.security.TOTPService
import io.github.rygel.outerstellar.platform.security.UserAdminService
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
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
        val messageService = mockk<MessageService>(relaxed = true)
        val repository = mockk<MessageRepository>(relaxed = true)
        val outbox = mockk<OutboxRepository>(relaxed = true)
        val cache = StubMessageCache()
        val pageFactory = WebPageFactory(repository, messageService, null, null)
        val config = AppConfig(port = 8080, jdbcUrl = "jdbc:postgresql://localhost/test", devDashboardEnabled = true)

        val apiKeyService = mockk<ApiKeyService>(relaxed = true)
        val passwordResetService = mockk<PasswordResetService>(relaxed = true)
        val oauthService = mockk<OAuthService>(relaxed = true)
        val authService = mockk<AuthService>(relaxed = true)
        val accountService = mockk<AccountService>(relaxed = true)
        val userRepository = mockk<UserRepository>(relaxed = true)
        val userAdminService = mockk<UserAdminService>(relaxed = true)
        val sessionService = mockk<SessionService>(relaxed = true)
        every { userRepository.countAll() } returns 0L
        val contactService = mockk<ContactService>(relaxed = true)

        val persistence =
            PersistenceComponents(
                dataSource = mockk(relaxed = true),
                jdbi = mockk(relaxed = true),
                messageRepository = repository,
                contactRepository = mockk(relaxed = true),
                userRepository = userRepository,
                outboxRepository = outbox,
                transactionManager = mockk(relaxed = true),
                auditRepository = mockk(relaxed = true),
                passwordResetRepository = mockk(relaxed = true),
                apiKeyRepository = mockk(relaxed = true),
                oAuthRepository = mockk(relaxed = true),
                deviceTokenRepository = mockk(relaxed = true),
                sessionRepository = mockk(relaxed = true),
                voteRepository = mockk(relaxed = true),
                pollRepository = mockk(relaxed = true),
                notificationRepository = mockk(relaxed = true),
            )

        val security =
            SecurityComponents(
                jwtService = mockk(relaxed = true),
                asyncActivityUpdater = mockk(relaxed = true),
                authService = authService,
                accountService = accountService,
                apiKeyService = apiKeyService,
                passwordResetService = passwordResetService,
                oauthService = oauthService,
                authRealms = emptyList(),
                totpService = TOTPService(),
                sessionService = sessionService,
                userAdminService = userAdminService,
            )

        val core =
            CoreComponents(
                messageService = messageService,
                contactService = contactService,
                outboxProcessor = mockk(relaxed = true),
                eventPublisher = io.github.rygel.outerstellar.platform.service.NoOpEventPublisher,
                emailService = io.github.rygel.outerstellar.platform.service.ConsoleEmailService(),
                pushNotificationService = io.github.rygel.outerstellar.platform.service.ConsolePushNotificationService,
            )

        val notificationService =
            mockk<io.github.rygel.outerstellar.platform.service.NotificationService>(relaxed = true)
        val voteRepository = mockk<io.github.rygel.outerstellar.platform.persistence.VoteRepository>(relaxed = true)
        val pollRepository = mockk<io.github.rygel.outerstellar.platform.persistence.PollRepository>(relaxed = true)

        val web =
            WebComponents(
                templateRenderer = createRenderer(),
                pageFactory = pageFactory,
                messageCache = cache,
                analyticsService = io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService(),
                emailService = io.github.rygel.outerstellar.platform.service.NoOpEmailService(),
                i18nService = io.github.rygel.outerstellar.i18n.I18nService.create("messages"),
                syncWebSocket = io.github.rygel.outerstellar.platform.web.SyncWebSocket(sessionService),
                eventPublisher = io.github.rygel.outerstellar.platform.service.NoOpEventPublisher,
                voteService = io.github.rygel.outerstellar.platform.service.VoteService(voteRepository, repository),
                pollService = io.github.rygel.outerstellar.platform.service.PollService(pollRepository),
                notificationService = notificationService,
                adminPageFactory =
                    io.github.rygel.outerstellar.platform.web.AdminPageFactory(
                        apiKeyService,
                        notificationService,
                        userAdminService,
                    ),
                adminStatsService = io.github.rygel.outerstellar.platform.security.AdminStatsService(userRepository),
                pluginMigrationSource = object : PluginMigrationSource {},
            )

        val polyHandler = app(config = config, persistence = persistence, security = security, core = core, web = web)
        val handler = polyHandler.http
        assertNotNull(handler)

        val healthResponse = handler(Request(Method.GET, "/health"))
        assertThat(healthResponse, hasStatus(Status.OK))
        assertThat(healthResponse, bodyContains("UP"))
    }
}
