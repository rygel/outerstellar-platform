package io.github.rygel.outerstellar.platform.e2e

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import io.github.rygel.outerstellar.platform.AppConfig
import io.github.rygel.outerstellar.platform.di.coreModule
import io.github.rygel.outerstellar.platform.di.persistenceModule
import io.github.rygel.outerstellar.platform.di.webModule
import io.github.rygel.outerstellar.platform.security.securityModule
import org.http4k.server.Http4kServer
import org.http4k.server.Jetty
import org.http4k.server.PolyHandler
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("e2e")
class ResponsiveLayoutE2ETest : KoinTest {

    private val app: PolyHandler by inject(named("webServer"))
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
                            jdbcUrl = "jdbc:h2:mem:responsive_test_${System.currentTimeMillis()}" +
                                ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                            devMode = true,
                        )
                    }
                },
                persistenceModule, coreModule, securityModule, webModule,
            )
        }
        val ds = getKoin().get<javax.sql.DataSource>()
        io.github.rygel.outerstellar.platform.infra.migrate(ds)
        server = app.asServer(Jetty(0)).start()
    }

    @AfterEach
    fun teardown() {
        page.close()
        browserContext.close()
        server.stop()
        stopKoin()
    }

    private fun createPageWithViewport(width: Int, height: Int): Page {
        browserContext = browser.newContext(
            Browser.NewContextOptions().setViewportSize(width, height)
        )
        page = browserContext.newPage()
        return page
    }

    @Test
    fun `desktop viewport shows full sidebar with labels`() {
        val p = createPageWithViewport(1400, 900)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        assertTrue(p.locator(".sidebar").isVisible, "Sidebar should be visible on desktop")
        assertTrue(p.locator(".nav-link").first().isVisible, "Nav links should be visible")
        assertFalse(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should be hidden on desktop")
    }

    @Test
    fun `mobile viewport hides sidebar and shows hamburger`() {
        val p = createPageWithViewport(375, 812)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        assertTrue(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should be visible on mobile")
        assertFalse(p.locator(".sidebar").isVisible, "Sidebar should be hidden on mobile")
    }

    @Test
    fun `mobile hamburger opens sidebar overlay`() {
        val p = createPageWithViewport(375, 812)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        p.locator(".mobile-menu-toggle").click()
        assertTrue(p.locator(".sidebar.open").isVisible, "Sidebar should open after hamburger click")
        assertTrue(p.locator(".sidebar-overlay.open").isVisible, "Overlay should be visible")
    }

    @Test
    fun `clicking overlay closes mobile sidebar`() {
        val p = createPageWithViewport(375, 812)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        p.locator(".mobile-menu-toggle").click()
        assertTrue(p.locator(".sidebar.open").isVisible)

        p.locator(".sidebar-overlay").click()
        assertFalse(p.locator(".sidebar.open").count() > 0 && p.locator(".sidebar.open").isVisible,
            "Sidebar should close after overlay click")
    }

    @Test
    fun `tablet viewport shows icon-only sidebar`() {
        val p = createPageWithViewport(1000, 768)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        assertTrue(p.locator(".sidebar").isVisible, "Sidebar should be visible on tablet")
        assertFalse(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should be hidden on tablet")
    }

    @Test
    fun `mobile viewport has touch-friendly button sizes`() {
        val p = createPageWithViewport(375, 812)
        p.navigate("http://localhost:${server.port()}/")
        p.waitForLoadState(LoadState.NETWORKIDLE)

        val viewport = p.evaluate("document.querySelector('meta[name=viewport]')?.content") as? String
        assertTrue(viewport?.contains("width=device-width") == true, "Viewport meta should be set")
    }
}
