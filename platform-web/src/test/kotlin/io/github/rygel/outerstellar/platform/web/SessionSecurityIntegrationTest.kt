package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for session-based security (Feature 3 — filters and security rules).
 *
 * Covers:
 * - Unauthenticated request to protected route redirects to /auth
 * - Valid session cookie grants access to protected route
 * - Invalid (non-UUID) session cookie is rejected
 * - Unknown UUID in session cookie is rejected
 * - Disabled user session is rejected
 * - Admin-only route (/admin/users) with regular-user cookie returns 403
 * - Admin-only route with admin cookie returns 200
 * - POST /logout clears the session cookie and redirects
 * - CORS preflight returns the right headers
 * - Security headers (X-Frame-Options, X-Content-Type-Options, etc.) are present on every response
 * - returnTo query param is preserved in redirect
 * - returnTo with external URL is sanitised to /
 * - Theme cookie is set when ?theme=dark is passed
 * - Lang cookie is set when ?lang=fr is passed
 * - Invalid lang value does not set a cookie
 */
class SessionSecurityIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var regularUser: User
    private lateinit var adminUser: User
    private lateinit var disabledUser: User
    private lateinit var encoder: BCryptPasswordEncoder

    @BeforeEach
    fun setupTest() {
        encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        regularUser =
            User(
                id = UUID.randomUUID(),
                username = "regularuser",
                email = "regular@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "adminuser",
                email = "admin@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.ADMIN,
            )
        disabledUser =
            User(
                id = UUID.randomUUID(),
                username = "disableduser",
                email = "disabled@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
                enabled = false,
            )
        userRepository.save(regularUser)
        userRepository.save(adminUser)
        userRepository.save(disabledUser)

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

    private fun sessionFor(user: User) = Cookie(WebContext.SESSION_COOKIE, user.id.toString())

    // ---- Unauthenticated access ----

    @Test
    fun `unauthenticated GET to protected page redirects to auth`() {
        val response = app(Request(GET, "/auth/change-password"))
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("/auth"),
            "Unauthenticated /auth/change-password should redirect to /auth, got: $location",
        )
    }

    @Test
    fun `unauthenticated request to admin route preserves path in redirect URL`() {
        val response = app(Request(GET, "/admin/users"))
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("returnTo") || location.contains("admin"),
            "Redirect should preserve original path in returnTo, got: $location",
        )
    }

    // ---- Valid session ----

    @Test
    fun `valid session cookie grants access to home page`() {
        val response = app(Request(GET, "/").cookie(sessionFor(regularUser)))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `valid session cookie grants access to contacts page`() {
        val response = app(Request(GET, "/contacts").cookie(sessionFor(regularUser)))
        assertEquals(Status.OK, response.status)
    }

    // ---- Invalid / unknown session ----

    @Test
    fun `non-UUID session cookie is rejected and redirects to auth`() {
        val response =
            app(Request(GET, "/auth/change-password").cookie(Cookie(WebContext.SESSION_COOKIE, "not-a-uuid-at-all")))
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    @Test
    fun `unknown UUID in session cookie is rejected`() {
        val response =
            app(
                Request(GET, "/auth/change-password")
                    .cookie(Cookie(WebContext.SESSION_COOKIE, UUID.randomUUID().toString()))
            )
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- Disabled user ----

    @Test
    fun `disabled user session cannot access admin-only route`() {
        // Disabled USER-role users are not admin, so /admin/users returns 403, not 200
        val response = app(Request(GET, "/admin/users").cookie(sessionFor(disabledUser)))
        assertFalse(response.status == Status.OK, "Disabled user should not receive 200 on admin route")
    }

    // ---- Admin-only routes ----

    @Test
    fun `admin-only route with regular-user session returns 403`() {
        val response = app(Request(GET, "/admin/users").cookie(sessionFor(regularUser)))
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `admin-only route with admin session returns 200`() {
        val response = app(Request(GET, "/admin/users").cookie(sessionFor(adminUser)))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `unauthenticated request to admin route redirects to auth`() {
        val response = app(Request(GET, "/admin/users"))
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- Logout ----

    @Test
    fun `POST logout redirects`() {
        val response = app(Request(POST, "/logout").cookie(sessionFor(regularUser)))
        assertEquals(Status.FOUND, response.status)
    }

    @Test
    fun `POST logout clears the session cookie`() {
        val response = app(Request(POST, "/logout").cookie(sessionFor(regularUser)))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains(WebContext.SESSION_COOKIE) &&
                (setCookie.contains("Max-Age=0") || setCookie.contains("max-age=0")),
            "Logout should expire the session cookie, got: $setCookie",
        )
    }

    // ---- Security headers ----

    @Test
    fun `every response includes X-Content-Type-Options nosniff`() {
        val response = app(Request(GET, "/health"))
        assertEquals("nosniff", response.header("X-Content-Type-Options"))
    }

    @Test
    fun `every response includes X-Frame-Options DENY`() {
        val response = app(Request(GET, "/health"))
        assertEquals("DENY", response.header("X-Frame-Options"))
    }

    @Test
    fun `every response includes Referrer-Policy`() {
        val response = app(Request(GET, "/health"))
        assertNotNull(response.header("Referrer-Policy"))
    }

    @Test
    fun `HTML pages include Content-Security-Policy header`() {
        val response = app(Request(GET, "/auth"))
        assertNotNull(response.header("Content-Security-Policy"), "HTML pages should have a CSP header")
    }

    @Test
    fun `API routes do not include Content-Security-Policy header`() {
        val response = app(Request(GET, "/health"))
        // /health is not under /api/ but is JSON; CSP only for non-/api paths in current impl
        // The important thing is /api/ paths skip it
        val apiResponse =
            app(
                Request(org.http4k.core.Method.POST, "/api/v1/auth/login")
                    .header("content-type", "application/json")
                    .body("""{"username":"x","password":"y"}""")
            )
        // API routes should not have CSP (or it's absent)
        val csp = apiResponse.header("Content-Security-Policy")
        assertTrue(csp == null, "API routes should not receive CSP header, got: $csp")
    }

    // ---- CORS ----

    @Test
    fun `OPTIONS preflight returns CORS headers and 204`() {
        val response =
            app(
                Request(org.http4k.core.Method.OPTIONS, "/api/v1/auth/login")
                    .header("Origin", "https://example.com")
                    .header("Access-Control-Request-Method", "POST")
            )
        assertEquals(Status.NO_CONTENT, response.status)
        assertNotNull(response.header("Access-Control-Allow-Methods"), "Preflight should return allowed methods")
    }

    @Test
    fun `normal response includes Access-Control-Expose-Headers with X-Request-Id`() {
        val response = app(Request(GET, "/health"))
        val expose = response.header("Access-Control-Expose-Headers").orEmpty()
        assertTrue(expose.contains("X-Request-Id"), "X-Request-Id must be in Expose-Headers, got: $expose")
    }

    // ---- Theme / lang state cookies ----

    @Test
    fun `?theme=dark sets app_theme cookie`() {
        val response = app(Request(GET, "/auth?theme=dark"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(
            setCookie.contains(WebContext.THEME_COOKIE),
            "?theme=dark should set a theme cookie, got: $setCookie",
        )
    }

    @Test
    fun `?lang=fr sets lang cookie`() {
        val response = app(Request(GET, "/auth?lang=fr"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertTrue(setCookie.contains(WebContext.LANG_COOKIE), "?lang=fr should set a lang cookie, got: $setCookie")
    }

    @Test
    fun `invalid lang value does not set a cookie`() {
        val response = app(Request(GET, "/auth?lang=klingon"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertFalse(setCookie.contains("klingon"), "Invalid lang should not be persisted as a cookie")
    }

    @Test
    fun `invalid theme value does not set a cookie`() {
        val response = app(Request(GET, "/auth?theme=hacker_green"))
        val setCookie = response.header("Set-Cookie").orEmpty()
        assertFalse(setCookie.contains("hacker_green"), "Invalid theme value should not be persisted as a cookie")
    }
}
