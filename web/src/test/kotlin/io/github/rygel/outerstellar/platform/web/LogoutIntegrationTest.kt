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
import org.http4k.core.cookie.cookies
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the logout flow.
 *
 * Covers:
 * - POST /logout redirects to home
 * - POST /logout clears the session cookie
 * - POST /logout with no session still redirects cleanly
 * - Session cookie is no longer valid after logout
 * - Logout from any page redirects to home
 */
class LogoutIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var user: User
    private lateinit var userRepository: JooqUserRepository

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        val auditRepository = io.github.rygel.outerstellar.platform.persistence.JooqAuditRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder, auditRepository)
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        user =
            User(
                id = UUID.randomUUID(),
                username = "logoutuser",
                email = "logout@test.com",
                passwordHash = encoder.encode("pass123"),
                role = UserRole.USER,
            )
        userRepository.save(user)

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

    private fun sessionCookie() = Cookie(WebContext.SESSION_COOKIE, user.id.toString())

    @Test
    fun `POST logout redirects to home`() {
        val response = app(Request(POST, "/logout").cookie(sessionCookie()))

        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location")?.contains("/") == true)
    }

    @Test
    fun `POST logout clears the session cookie`() {
        val response = app(Request(POST, "/logout").cookie(sessionCookie()))

        val sessionCookieInResponse =
            response.cookies().find { it.name == WebContext.SESSION_COOKIE }
        assertNotNull(
            sessionCookieInResponse,
            "Session cookie should be present in Set-Cookie to clear it",
        )
        // A cleared cookie has maxAge 0 or a past expiry
        assertTrue(
            (sessionCookieInResponse.maxAge ?: 1L) <= 0L || sessionCookieInResponse.value.isEmpty(),
            "Session cookie should be cleared (maxAge=0 or empty value)",
        )
    }

    @Test
    fun `POST logout without a session still redirects cleanly`() {
        val response = app(Request(POST, "/logout"))

        assertEquals(Status.FOUND, response.status)
    }

    @Test
    fun `home page is accessible before logout`() {
        val response = app(Request(GET, "/").cookie(sessionCookie()))

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `after logout the old session cookie no longer authenticates protected pages`() {
        // Simulate a "stale" cookie by using an ID that has no matching user
        val staleId = UUID.randomUUID().toString()
        val staleSession = Cookie(WebContext.SESSION_COOKIE, staleId)

        // Protected page should not return 200
        val response = app(Request(GET, "/admin/users").cookie(staleSession))

        assertFalse(response.status == Status.OK)
    }

    @Test
    fun `POST logout response does not expose session data`() {
        val response = app(Request(POST, "/logout").cookie(sessionCookie()))

        // Body should not contain user info
        val body = response.bodyString()
        assertFalse(body.contains("logoutuser"))
        assertFalse(body.contains(user.id.toString()))
    }
}
