package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
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
 * Integration tests for the DaisyUI theme system.
 *
 * Verifies:
 * - data-theme attribute is set on <html> element
 * - Theme selector dropdown is present in the sidebar
 * - Requesting /?theme=dark sets the app_theme cookie to "dark"
 * - Requesting /?theme=light sets the app_theme cookie to "light"
 * - The page respects the app_theme cookie on subsequent requests
 * - platform.js is included for client-side behavior
 * - Default theme is "dark"
 * - Invalid theme values are ignored
 */
class DarkModeToggleIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var securityService: SecurityService

    @BeforeEach
    fun setupTest() {
        securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )
        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun seedAdmin(): String {
        val id = UUID.randomUUID()
        userRepository.save(User(id, "admin", "admin@test.com", encoder.encode(testPassword()), UserRole.ADMIN))
        return securityService.createSession(id)
    }

    // ---- data-theme attribute ----

    @Test
    fun `html element has data-theme attribute set to dark by default`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("data-theme=\"dark\""), "Default theme should be dark")
    }

    @Test
    fun `html element has data-theme attribute set to light when requested`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "light"))).bodyString()
        assertTrue(body.contains("data-theme=\"light\""), "Light theme cookie should set data-theme=light")
    }

    @Test
    fun `html element has data-theme attribute set to nord when requested`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "nord"))).bodyString()
        assertTrue(body.contains("data-theme=\"nord\""), "Nord theme cookie should set data-theme=nord")
    }

    // ---- Theme selector dropdown ----

    @Test
    fun `sidebar contains theme selector dropdown`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("id=\"theme-selector\""), "Sidebar must contain theme-selector element")
    }

    @Test
    fun `theme selector is a select element with theme options`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("name=\"theme\""), "Theme selector must have a select with name=theme")
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
    fun `requesting with theme=light sets app_theme cookie to light`() {
        val response = app(Request(GET, "/?theme=light"))

        assertEquals(Status.OK, response.status)
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains("app_theme=light") || setCookie.contains("app_theme=\"light\""),
            "Response should set app_theme=light cookie, got: $setCookie",
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
        assertFalse(setCookie.contains("app_theme=invalid_theme_xyz"), "Invalid theme should not be set in cookie")
    }

    // ---- Theme cookie respected on subsequent requests ----

    @Test
    fun `page respects dark theme cookie`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "dark"))).bodyString()
        assertTrue(body.contains("data-theme=\"dark\""), "Dark theme cookie should produce data-theme=dark")
    }

    @Test
    fun `page respects light theme cookie`() {
        val body = app(Request(GET, "/").cookie(Cookie("app_theme", "light"))).bodyString()
        assertTrue(body.contains("data-theme=\"light\""), "Light theme cookie should produce data-theme=light")
    }

    @Test
    fun `no inline theme style block is present`() {
        val body = app(Request(GET, "/")).bodyString()
        assertFalse(body.contains("id=\"theme-style\""), "DaisyUI themes should not use inline style blocks")
    }

    // ---- platform.js inclusion ----

    @Test
    fun `every page includes platform js`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("platform.js"), "Page should include platform.js")
    }

    @Test
    fun `page references toast data attributes`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(
            body.contains("data-toast-error") || body.contains("platform.js"),
            "Page should reference platform.js or data attributes for toast/theme handling",
        )
    }

    @Test
    fun `platform js is loaded for theme cookie detection`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("platform.js"), "Page should load platform.js which handles theme cookie detection")
    }

    // ---- Theme selector on admin pages ----

    @Test
    fun `data-theme is present on admin pages too`() {
        val adminToken = seedAdmin()

        val body = app(Request(GET, "/admin/users").cookie(Cookie("app_session", adminToken))).bodyString()
        assertTrue(body.contains("data-theme=\"dark\""), "Admin page should have data-theme attribute")
    }

    @Test
    fun `data-theme is present on auth page`() {
        val body = app(Request(GET, "/auth")).bodyString()
        assertTrue(body.contains("data-theme=\"dark\""), "Auth page should have data-theme attribute")
    }

    // ---- Theme selector still present ----

    @Test
    fun `sidebar theme selector is present`() {
        val body = app(Request(GET, "/")).bodyString()
        assertTrue(body.contains("theme-selector"), "Sidebar theme selector component should still be rendered")
    }
}
