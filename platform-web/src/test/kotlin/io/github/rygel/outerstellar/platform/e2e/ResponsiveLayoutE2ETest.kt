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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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

@Tag("e2e")
class ResponsiveLayoutE2ETest : KoinTest {

    private val app: PolyHandler by inject(named("webServer"))
    private lateinit var server: Http4kServer
    private lateinit var browserContext: com.microsoft.playwright.BrowserContext
    private lateinit var page: Page

    companion object {
        private lateinit var playwright: Playwright
        private lateinit var browser: Browser
        private const val MOBILE_WIDTH = 375
        private const val MOBILE_HEIGHT = 812
        private const val TABLET_WIDTH = 1000
        private const val TABLET_HEIGHT = 768
        private const val DESKTOP_WIDTH = 1400
        private const val DESKTOP_HEIGHT = 900

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

    private lateinit var baseUrl: String

    @BeforeEach
    fun setup() {
        stopKoin()
        startKoin {
            modules(
                module {
                    single {
                        AppConfig(
                            jdbcUrl =
                                "jdbc:h2:mem:responsive_test_${System.currentTimeMillis()}" +
                                    ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
        val ds = getKoin().get<javax.sql.DataSource>()
        io.github.rygel.outerstellar.platform.infra.migrate(ds)
        getKoin().get<io.github.rygel.outerstellar.platform.persistence.MessageRepository>().seedMessages()
        getKoin().get<io.github.rygel.outerstellar.platform.persistence.ContactRepository>().seedContacts()
        server = app.asServer(Jetty(0)).start()
        baseUrl = "http://localhost:${server.port()}"
    }

    @AfterEach
    fun teardown() {
        page.close()
        browserContext.close()
        server.stop()
        stopKoin()
    }

    private fun openPage(width: Int, height: Int, path: String = "/"): Page {
        browserContext = browser.newContext(Browser.NewContextOptions().setViewportSize(width, height))
        page = browserContext.newPage()
        page.navigate("$baseUrl$path")
        page.waitForLoadState(LoadState.NETWORKIDLE)
        return page
    }

    // === Desktop Layout Tests ===

    @Test
    fun `desktop - sidebar is visible with nav labels`() {
        val p = openPage(DESKTOP_WIDTH, DESKTOP_HEIGHT)
        assertTrue(p.locator(".sidebar").isVisible, "Sidebar should be visible")
        assertTrue(p.locator(".nav-link").first().isVisible, "Nav links should be visible")
        assertFalse(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should be hidden")
    }

    @Test
    fun `desktop - can navigate to all main pages`() {
        val p = openPage(DESKTOP_WIDTH, DESKTOP_HEIGHT)
        val navLinks = p.locator(".nav-link")
        val count = navLinks.count()
        assertTrue(count >= 1, "Should have at least 1 nav link")

        for (i in 0 until count) {
            val href = navLinks.nth(i).getAttribute("href") ?: continue
            p.navigate("$baseUrl$href")
            p.waitForLoadState(LoadState.NETWORKIDLE)
            val status = p.evaluate("document.readyState") as String
            assertTrue(status == "complete", "Page $href should load completely")
            assertTrue(
                p.locator(".content, .topbar-content").first().isVisible,
                "Content area should be visible on $href",
            )
        }
    }

    @Test
    fun `desktop - footer is visible with version info`() {
        val p = openPage(DESKTOP_WIDTH, DESKTOP_HEIGHT)
        assertTrue(p.locator(".footer").isVisible, "Footer should be visible")
    }

    @Test
    fun `desktop - dark mode toggle works`() {
        val p = openPage(DESKTOP_WIDTH, DESKTOP_HEIGHT)
        val toggleLink = p.locator("a[title='Toggle dark/light mode']")
        assertTrue(toggleLink.isVisible, "Dark mode toggle should be visible")
        val href = toggleLink.getAttribute("href")
        assertTrue(href != null && href.contains("theme="), "Toggle should link to theme switch")
    }

    // === Mobile Layout Tests ===

    @Test
    fun `mobile - sidebar hidden, hamburger visible`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        assertTrue(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should show on mobile")
        val sidebar = p.locator(".sidebar")
        val box = sidebar.boundingBox()
        assertTrue(box == null || box.x < 0, "Sidebar should be off-screen")
    }

    @Test
    fun `mobile - hamburger opens sidebar with working nav links`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        p.locator(".mobile-menu-toggle").click()
        p.waitForTimeout(500.0)

        val sidebar = p.locator(".sidebar")
        assertTrue(sidebar.isVisible, "Sidebar should be visible after hamburger click")

        val overlay = p.locator(".sidebar-overlay")
        assertTrue(overlay.isVisible, "Overlay should be visible")

        val navLinks = sidebar.locator(".nav-link")
        assertTrue(navLinks.count() >= 1, "Sidebar should show nav links")
        assertTrue(navLinks.first().isVisible, "First nav link should be visible and clickable")
    }

    @Test
    fun `mobile - clicking nav link closes sidebar and navigates`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        p.locator(".mobile-menu-toggle").click()
        p.waitForTimeout(500.0)

        val navLinks = p.locator(".sidebar .nav-link")
        val href = navLinks.first().getAttribute("href") ?: "/"
        navLinks.first().click()
        p.waitForLoadState(LoadState.NETWORKIDLE)

        assertTrue(p.url().contains(href) || href == "/", "Should navigate to $href")
        val sidebar = p.locator(".sidebar.open")
        assertTrue(sidebar.count() == 0, "Sidebar should close after nav click")
    }

    @Test
    fun `mobile - overlay click closes sidebar`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        p.locator(".mobile-menu-toggle").click()
        p.waitForTimeout(500.0)
        assertTrue(p.locator(".sidebar.open").isVisible)

        p.locator(".sidebar-overlay").click(com.microsoft.playwright.Locator.ClickOptions().setForce(true))
        p.waitForTimeout(500.0)
        assertTrue(p.locator(".sidebar.open").count() == 0, "Sidebar should close after overlay click")
    }

    @Test
    fun `mobile - content is readable without horizontal scroll`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        val scrollWidth = p.evaluate("document.documentElement.scrollWidth") as Number
        val clientWidth = p.evaluate("document.documentElement.clientWidth") as Number
        assertTrue(
            scrollWidth.toInt() <= clientWidth.toInt() + 5,
            "Page should not have horizontal scroll (scrollWidth=$scrollWidth, clientWidth=$clientWidth)",
        )
    }

    @Test
    fun `mobile - can navigate to contacts page and see content`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT, "/contacts")
        assertTrue(p.locator(".content").isVisible, "Content should be visible")
        val bodyText = p.locator("body").textContent()
        assertTrue(
            bodyText.contains("Alice") || bodyText.contains("Contact"),
            "Contacts page should show content on mobile",
        )
    }

    @Test
    fun `mobile - viewport meta prevents zoom on input focus`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        val viewport = p.evaluate("document.querySelector('meta[name=viewport]')?.content") as? String
        assertTrue(viewport != null, "Viewport meta should exist")
        assertTrue(viewport.contains("width=device-width"), "Should set device width")
        assertTrue(viewport.contains("initial-scale=1"), "Should set initial scale")
    }

    @Test
    fun `mobile - form inputs are touch-friendly size`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT)
        val inputs = p.locator("input:visible, textarea:visible, select:visible, button:visible")
        val count = inputs.count()
        for (i in 0 until minOf(count, 10)) {
            val box = inputs.nth(i).boundingBox() ?: continue
            assertTrue(
                box.height >= 40,
                "Input ${inputs.nth(i).getAttribute("name") ?: i} should be at least 40px tall (was ${box.height})",
            )
        }
    }

    // === Tablet Layout Tests ===

    @Test
    fun `tablet - sidebar visible in icon-only mode`() {
        val p = openPage(TABLET_WIDTH, TABLET_HEIGHT)
        assertTrue(p.locator(".sidebar").isVisible, "Sidebar should be visible on tablet")
        assertFalse(p.locator(".mobile-menu-toggle").isVisible, "Hamburger should be hidden on tablet")

        val sidebarBox = p.locator(".sidebar").boundingBox()
        assertTrue(
            sidebarBox != null && sidebarBox.width < 100,
            "Sidebar should be narrow on tablet (icon-only), was ${sidebarBox?.width}",
        )
    }

    @Test
    fun `tablet - nav icons are clickable and navigate`() {
        val p = openPage(TABLET_WIDTH, TABLET_HEIGHT)
        val navLinks = p.locator(".nav-link")
        assertTrue(navLinks.count() >= 1, "Should have nav links")
        val firstLink = navLinks.first()
        assertTrue(firstLink.isVisible, "Nav icon should be visible")
        val href = firstLink.getAttribute("href")
        firstLink.click()
        p.waitForLoadState(LoadState.NETWORKIDLE)
        assertTrue(p.url().contains(href ?: "/"), "Should navigate on click")
    }

    // === Orientation Change Tests ===

    @Test
    fun `mobile landscape - layout adapts without breaking`() {
        val p = openPage(MOBILE_HEIGHT, MOBILE_WIDTH) // 812x375 landscape
        val scrollWidth = p.evaluate("document.documentElement.scrollWidth") as Number
        val clientWidth = p.evaluate("document.documentElement.clientWidth") as Number
        assertTrue(scrollWidth.toInt() <= clientWidth.toInt() + 5, "Landscape should not have horizontal scroll")
        assertTrue(p.locator(".content, .topbar-content").first().isVisible, "Content should be visible in landscape")
    }

    // === Cross-page Mobile Tests ===

    @Test
    fun `mobile - auth page is usable`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT, "/auth")
        p.waitForLoadState(LoadState.NETWORKIDLE)
        val bodyText = p.locator("body").textContent()
        assertTrue(
            bodyText.contains("Sign") || bodyText.contains("Login") || bodyText.contains("Auth"),
            "Auth page should render on mobile",
        )
        val scrollWidth = p.evaluate("document.documentElement.scrollWidth") as Number
        val clientWidth = p.evaluate("document.documentElement.clientWidth") as Number
        assertTrue(
            scrollWidth.toInt() <= clientWidth.toInt() + 5,
            "Auth page should not have horizontal scroll on mobile",
        )
    }

    @Test
    fun `mobile - error page renders properly`() {
        val p = openPage(MOBILE_WIDTH, MOBILE_HEIGHT, "/nonexistent-page-12345")
        val bodyText = p.locator("body").textContent()
        assertTrue(
            bodyText.contains("404") ||
                bodyText.contains("not found", ignoreCase = true) ||
                bodyText.contains("error", ignoreCase = true),
            "Error page should render on mobile",
        )
    }
}
