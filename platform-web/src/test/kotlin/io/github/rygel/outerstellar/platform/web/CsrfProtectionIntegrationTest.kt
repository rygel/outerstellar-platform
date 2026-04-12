package io.github.rygel.outerstellar.platform.web

import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Integration tests for CSRF protection.
 *
 * Covers:
 * - POST without CSRF cookie/token is rejected (403)
 * - POST with matching cookie + form field is accepted
 * - POST with matching cookie + header is accepted
 * - POST with cookie but wrong form token is rejected
 * - GET requests are not affected
 * - API routes (/api/v1/) are exempt
 * - OAuth routes (/oauth/) are exempt
 */
class CsrfProtectionIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

    @BeforeEach
    fun setupTest() {
        app = buildApp(config = testConfig.copy(csrfEnabled = true))
    }

    @AfterEach fun teardown() = cleanup()

    private fun csrfCookie(token: String) = Cookie(WebContext.CSRF_COOKIE, token)

    @Test
    fun `POST without CSRF cookie is rejected with 403`() {
        val response = app(Request(POST, "/logout").body(""))

        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `POST without CSRF form field is rejected with 403`() {
        val token = "test-csrf-token"
        val response =
            app(
                Request(POST, "/logout")
                    .cookie(csrfCookie(token))
                    .header("content-type", "application/x-www-form-urlencoded")
                    // no _csrf field in body
                    .body("other_field=value")
            )

        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `POST with matching cookie and form field is accepted`() {
        val token = "test-csrf-token"
        val response =
            app(
                Request(POST, "/logout")
                    .cookie(csrfCookie(token))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("_csrf=$token")
            )

        // Logout redirects (302) when CSRF passes, not 403
        assertNotEquals(Status.FORBIDDEN, response.status)
        assertEquals(Status.FOUND, response.status)
    }

    @Test
    fun `POST with matching cookie and X-CSRF-Token header is accepted`() {
        val token = "test-csrf-token"
        val response = app(Request(POST, "/logout").cookie(csrfCookie(token)).header("X-CSRF-Token", token).body(""))

        assertNotEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `POST with wrong CSRF token is rejected`() {
        val response =
            app(
                Request(POST, "/logout")
                    .cookie(csrfCookie("correct-token"))
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("_csrf=wrong-token")
            )

        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `GET requests are not blocked by CSRF`() {
        val response = app(Request(GET, "/"))

        assertNotEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `API routes are exempt from CSRF`() {
        // POST to an API route without CSRF token — should not be 403 from CSRF filter
        val response =
            app(
                Request(POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"x","password":"y"}""")
            )

        // Should get 401/400/etc. — not 403 from CSRF
        assertNotEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `GET sets CSRF cookie when none exists`() {
        val response = app(Request(GET, "/"))

        val csrfCookieInResponse = response.cookies().find { it.name == WebContext.CSRF_COOKIE }
        assert(csrfCookieInResponse != null) { "Expected _csrf cookie to be set on first GET" }
    }
}
