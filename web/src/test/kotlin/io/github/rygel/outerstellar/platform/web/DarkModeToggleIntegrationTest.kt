package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the dark mode toggle button (Feature 2).
 *
 * Verifies:
 * - Dark mode toggle button (sun/moon icon) is rendered in the topbar
 * - The toggle URL correctly points to the opposite theme
 * - Requesting /?theme=dark sets the app_theme cookie to "dark"
 * - Requesting /?theme=default sets the app_theme cookie to "default"
 * - The page respects the app_theme cookie on subsequent requests
 * - System preference detection JS is injected into every page
 * - Dark mode CSS variables are applied for dark theme
 */
class DarkModeToggleIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var encoder: BCryptPasswordEncoder

    @BeforeEach
    fun setupTest() {
        userRepository = JooqUserRepository(testDsl)
        encoder = BCryptPasswordEncoder(logRounds = 4)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        app =
            app(
                    messageService,
                    contactService,
                    outbox,
                    cache,
                    createRenderer(),
                    pageFactory,
                    testConfig,
                    securityService,
                    userRepository,
                )
                .http!!
    }

    @AfterEach fun teardown() = cleanup()

    private fun seedAdmin(): UUID {
        val id = UUID.randomUUID()
        userRepository.save(
            User(id, "admin", "admin@test.com", encoder.encode("pass1234"), UserRole.ADMIN)
        )
        return id
    }

    // ---- Toggle button rendering ----

    @Test
    fun `topbar contains dark mode toggle button`() {
        val body = app(Request(GET, "/")).bodyString()

        // The toggle button has ri-sun-line (on dark) or ri-moon-line (on light)
        val hasSun = body.contains("ri-sun-line")
        val hasMoon = body.contains("ri-moon-line")
        assertTrue(
            hasSun || hasMoon,
            "Topbar must have a sun or moon icon for the dark mode toggle",
        )
    }

    @Test
    fun `dark theme page shows sun icon to switch to light`() {
        // Default theme is dark — toggle should show sun (click to go light)
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("ri-sun-line"), "Dark mode page should show sun icon")
    }

    @Test
    fun `light theme page shows moon icon to switch to dark`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "default"))).bodyString()
        assertTrue(body.contains("ri-moon-line"), "Light mode page should show moon icon")
    }

    @Test
    fun `dark theme toggle URL points to default theme`() {
        // Server defaults to dark; the toggle URL should switch to default (light)
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("?theme=default"),
            "Dark mode page toggle should link to ?theme=default",
        )
    }

    @Test
    fun `light theme toggle URL points to dark theme`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "default"))).bodyString()
        assertTrue(
            body.contains("?theme=dark"),
            "Light mode page toggle should link to ?theme=dark",
        )
    }

    // ---- Cookie setting via ?theme query param ----

    @Test
    fun `requesting with theme=dark sets app_theme cookie to dark`() {
        val response = app(Request(GET, "/?theme=dark"))

        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains("app_theme=dark") || setCookie.contains("app_theme=\"dark\""),
            "Response should set app_theme=dark cookie, got: $setCookie",
        )
    }

    @Test
    fun `requesting with theme=default sets app_theme cookie to default`() {
        val response = app(Request(GET, "/?theme=default"))

        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains("app_theme=default") || setCookie.contains("app_theme=\"default\""),
            "Response should set app_theme=default cookie, got: $setCookie",
        )
    }

    @Test
    fun `theme cookie is persistent (max-age is a full year)`() {
        val response = app(Request(GET, "/?theme=dark"))
        val setCookie = response.header("Set-Cookie").orEmpty()

        // 365 days in seconds = 31536000
        assertTrue(
            setCookie.contains("Max-Age=31536000") || setCookie.contains("max-age=31536000"),
            "Theme cookie should persist for 365 days, got: $setCookie",
        )
    }

    @Test
    fun `invalid theme value is ignored and cookie is not set`() {
        val response = app(Request(GET, "/?theme=invalid_theme_xyz"))

        val setCookie = response.header("Set-Cookie").orEmpty()
        assertFalse(
            setCookie.contains("app_theme=invalid_theme_xyz"),
            "Invalid theme should not be set in cookie",
        )
    }

    // ---- Theme cookie respected on subsequent requests ----

    @Test
    fun `page respects dark theme cookie`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "dark"))).bodyString()

        // Dark theme should show the sun icon (to toggle to light)
        assertTrue(body.contains("ri-sun-line"), "Dark theme cookie should produce sun icon")
    }

    @Test
    fun `page respects light theme cookie`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "default"))).bodyString()

        // Light theme should show the moon icon (to toggle to dark)
        assertTrue(body.contains("ri-moon-line"), "Default theme cookie should produce moon icon")
    }

    @Test
    fun `theme id is embedded in page when dark`() {
        val body = app(Request(GET, "/")).bodyString()
        // The theme CSS variables are rendered; dark theme has a dark background variable
        // We can check that the dark theme CSS variables block is present
        assertTrue(
            body.contains("theme-style"),
            "Page should have an inline style block with theme CSS variables",
        )
    }

    // ---- System preference detection JS ----

    @Test
    fun `every page includes system preference detection script`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("prefers-color-scheme"),
            "Page should include system color-scheme detection script",
        )
    }

    @Test
    fun `system preference script checks for existing app_theme cookie`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("app_theme"),
            "System preference script should reference the app_theme cookie name",
        )
    }

    @Test
    fun `system preference script only runs when no cookie is set`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("hasCookie") || body.contains("startsWith('app_theme=')"),
            "Script should check whether a theme cookie already exists",
        )
    }

    // ---- Toggle button on admin pages ----

    @Test
    fun `dark mode toggle is present on admin pages too`() {
        val adminId = seedAdmin()

        val body =
            app(Request(GET, "/admin/users").cookie(Cookie("app_session", adminId.toString())))
                .bodyString()

        val hasSun = body.contains("ri-sun-line")
        val hasMoon = body.contains("ri-moon-line")
        assertTrue(hasSun || hasMoon, "Admin page topbar should also have dark mode toggle")
    }

    @Test
    fun `dark mode toggle is present on auth page`() {
        val body = app(Request(GET, "/auth")).bodyString()
        val hasSun = body.contains("ri-sun-line")
        val hasMoon = body.contains("ri-moon-line")
        assertTrue(hasSun || hasMoon, "Auth page should have dark mode toggle")
    }

    // ---- Theme selector still works alongside the toggle ----

    @Test
    fun `sidebar theme selector is still present when dark mode toggle exists`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("theme-selector"),
            "Sidebar theme selector component should still be rendered",
        )
    }
}
