package io.github.rygel.outerstellar.platform.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.di.createCoreComponents
import io.github.rygel.outerstellar.platform.di.createPersistenceComponents
import io.github.rygel.outerstellar.platform.di.createWebComponents
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.security.createSecurityComponents
import kotlin.test.assertTrue
import org.http4k.core.PolyHandler
import org.http4k.server.Http4kServer
import org.http4k.server.Netty
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer

@Tag("e2e")
class PlaywrightE2ETest {

    private lateinit var messageRepo: MessageRepository
    private lateinit var contactRepo: ContactRepository
    private lateinit var server: Http4kServer
    private lateinit var browserContext: com.microsoft.playwright.BrowserContext
    private lateinit var page: Page

    companion object {
        private lateinit var playwright: Playwright
        private lateinit var browser: Browser

        private val container =
            PostgreSQLContainer<Nothing>("postgres:18").apply {
                withDatabaseName("outerstellar")
                withUsername("outerstellar")
                withPassword("outerstellar")
                start()
            }

        @JvmStatic
        @BeforeAll
        fun setupPlaywright() {
            playwright = Playwright.create()
            browser = playwright.chromium().launch()
        }

        @JvmStatic
        @AfterAll
        fun teardownPlaywright() {
            browser.close()
            playwright.close()
        }
    }

    @BeforeEach
    fun setup() {
        val testConfig =
            AppConfig(
                jdbcUrl = container.jdbcUrl,
                jdbcUser = container.username,
                jdbcPassword = container.password,
                devMode = true,
            )

        val persistence = createPersistenceComponents(testConfig)
        val security =
            createSecurityComponents(
                config = testConfig,
                userRepository = persistence.userRepository,
                auditRepository = persistence.auditRepository,
                resetRepository = persistence.passwordResetRepository,
                apiKeyRepository = persistence.apiKeyRepository,
                oauthRepository = persistence.oAuthRepository,
                sessionRepository = persistence.sessionRepository,
            )
        val web =
            createWebComponents(
                config = testConfig,
                securityService = security.securityService,
                messageRepository = persistence.messageRepository,
                userRepository = persistence.userRepository,
                voteRepository = persistence.voteRepository,
                pollRepository = persistence.pollRepository,
                notificationRepository = persistence.notificationRepository,
            )
        val core =
            createCoreComponents(
                config = testConfig,
                messageRepository = persistence.messageRepository,
                contactRepository = persistence.contactRepository,
                outboxRepository = persistence.outboxRepository,
                messageCache = web.messageCache,
                transactionManager = persistence.transactionManager,
                auditRepository = persistence.auditRepository,
                eventPublisher = web.eventPublisher,
                emailService = web.emailService,
            )

        messageRepo = persistence.messageRepository
        contactRepo = persistence.contactRepository

        messageRepo.seedMessages()
        contactRepo.seedContacts()

        val polyHandler: PolyHandler =
            app(
                messageService = core.messageService,
                contactService = core.contactService,
                outboxRepository = persistence.outboxRepository,
                cache = web.messageCache,
                jteRenderer = web.templateRenderer,
                pageFactory = web.pageFactory,
                config = testConfig,
                securityService = security.securityService,
                userRepository = persistence.userRepository,
                analytics = web.analyticsService,
                notificationService = web.notificationService,
                jwtService = security.jwtService,
                syncWebSocket = web.syncWebSocket,
                voteService = web.voteService,
                pollService = web.pollService,
            )
        server = polyHandler.asServer(Netty(0)).start()

        browserContext = browser.newContext()
        page = browserContext.newPage()
    }

    @AfterEach
    fun teardown() {
        page.close()
        browserContext.close()
        server.stop()
    }

    @Test
    fun `should load home page and verify title`() {
        page.navigate("http://localhost:${server.port()}")
        assertTrue(page.title().contains("Home"), "Title should contain Home")
        assertTrue(page.content().contains("Outerstellar Platform"))
    }

    @Test
    fun `should navigate to contacts page and show seeded data`() {
        page.navigate("http://localhost:${server.port()}/contacts")
        assertTrue(page.title().contains("Contacts"), "Title should contain Contacts")
        assertTrue(page.locator("text=Alice Smith").isVisible)
        assertTrue(page.locator("text=Bob Johnson").isVisible)
    }

    @Test
    fun `should be able to create a new message`() {
        page.navigate("http://localhost:${server.port()}/")
        page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE)

        page.locator("input[name='author']").fill("Playwright Tester")
        page.locator("textarea[name='content']").fill("Hello from E2E test!")

        page.waitForResponse({ response -> response.request().method() == "POST" }) {
            page.locator("form.form-grid button[type='submit']").click()
        }

        page.navigate("http://localhost:${server.port()}/")
        page.waitForSelector("text=Playwright Tester")

        val content = page.content()
        assertTrue(content.contains("Playwright Tester"), "Message author not found in page")
        assertTrue(content.contains("Hello from E2E test!"), "Message content not found in page")
    }
}
