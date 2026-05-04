package io.github.rygel.outerstellar.platform.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.di.webModule
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.security.securityModule
import kotlin.test.assertTrue
import org.http4k.core.PolyHandler
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import org.testcontainers.containers.PostgreSQLContainer

@Tag("e2e")
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
        stopKoin()
        startKoin {
            modules(
                module {
                    single {
                        AppConfig(
                            jdbcUrl = container.jdbcUrl,
                            jdbcUser = container.username,
                            jdbcPassword = container.password,
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
        io.github.rygel.outerstellar.platform.infra.migrate(ds)

        messageRepo.seedMessages()
        contactRepo.seedContacts()

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

        // Use a specific selector scoped to the message composer form so we don't
        // accidentally click the logout or other submit buttons on the page.
        // The form uses hx-post; HTMX sends XHR (not native form submit) — wait for
        // the POST response to complete, then navigate explicitly for fresh SSR.
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
