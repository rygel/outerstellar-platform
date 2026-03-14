package dev.outerstellar.starter.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import dev.outerstellar.starter.AppConfig
import dev.outerstellar.starter.di.coreModule
import dev.outerstellar.starter.di.persistenceModule
import dev.outerstellar.starter.di.webModule
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.security.securityModule
import kotlin.test.assertTrue
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject

class PlaywrightE2ETest : KoinTest {

    private val app: PolyHandler by inject(named("webServer"))
    private val messageRepo: MessageRepository by inject()
    private val contactRepo: ContactRepository by inject()

    private lateinit var server: Http4kServer
    private lateinit var browserContext: com.microsoft.playwright.BrowserContext
    private lateinit var page: Page

    companion object {
        private lateinit var playwright: Playwright
        private lateinit var browser: Browser

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
        stopKoin()
        startKoin {
            modules(
                module {
                    single {
                        AppConfig(
                            jdbcUrl =
                                "jdbc:h2:mem:playwright_test_${System.currentTimeMillis()};MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                            devMode = true,
                        )
                    }
                },
                persistenceModule,
                coreModule,
                securityModule,
                webModule,
            )
        }

        // Manual migration and seeding for the test DB
        val ds = getKoin().get<javax.sql.DataSource>()
        dev.outerstellar.starter.infra.migrate(ds)

        messageRepo.seedStarterMessages()
        contactRepo.seedStarterContacts()

        // Start real HTTP server on random port
        server = app.asServer(Jetty(0)).start()

        browserContext = browser.newContext()
        page = browserContext.newPage()
    }

    @AfterEach
    fun teardown() {
        page.close()
        browserContext.close()
        server.stop()
        stopKoin()
    }

    @Test
    fun `should load home page and verify title`() {
        page.navigate("http://localhost:${server.port()}")
        assertTrue(page.title().contains("Home"), "Title should contain Home")
        assertTrue(page.content().contains("Outerstellar Starter"))
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

        page.fill("input[name='author']", "Playwright Tester")
        page.fill("textarea[name='content']", "Hello from E2E test!")
        page.click("button[type='submit']")

        // Wait for page to reload and show the new message
        page.waitForSelector("text=Playwright Tester")
        assertTrue(page.locator("text=Hello from E2E test!").isVisible)
    }
}
