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
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for login returnTo redirect handling and external URL sanitisation.
 *
 * Covers:
 * - Successful login with valid returnTo redirects to that path
 * - returnTo with an external URL is sanitised to "/"
 * - returnTo with "//" (protocol-relative) is sanitised to "/"
 * - returnTo with blank value redirects to "/"
 * - No returnTo redirects to "/"
 * - Failed login ignores returnTo (renders error fragment)
 */
class LoginReturnToIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var testUserPassword: String

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        testUserPassword = testPassword()
        testUser =
            User(
                id = UUID.randomUUID(),
                username = "returntouser@test.com",
                email = "returntouser@test.com",
                passwordHash = encoder.encode(testUserPassword),
                role = UserRole.USER,
            )
        userRepository.save(testUser)

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

    private fun loginRequest(returnTo: String? = null): org.http4k.core.Response {
        val url =
            if (returnTo != null) {
                "/auth/components/result?returnTo=$returnTo"
            } else {
                "/auth/components/result"
            }
        return app(
            Request(POST, url)
                .header("content-type", "application/x-www-form-urlencoded")
                .body("mode=sign-in&email=${testUser.email}&password=$testUserPassword")
        )
    }

    @Test
    fun `successful login without returnTo redirects to root`() {
        val response = loginRequest()
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/", location, "Should redirect to / when no returnTo")
    }

    @Test
    fun `successful login with valid internal returnTo redirects there`() {
        val response = loginRequest(returnTo = "/messages")
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/messages", location, "Should redirect to /messages")
    }

    @Test
    fun `successful login with nested internal returnTo redirects there`() {
        val response = loginRequest(returnTo = "/admin/users")
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/admin/users", location, "Should redirect to /admin/users")
    }

    @Test
    fun `returnTo with external http URL is sanitised to root`() {
        val response = loginRequest(returnTo = "https://evil.com/steal")
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/", location, "External URL should be sanitised to /")
    }

    @Test
    fun `returnTo with protocol-relative URL is sanitised to root`() {
        val response = loginRequest(returnTo = "//evil.com/steal")
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/", location, "Protocol-relative URL should be sanitised to /")
    }

    @Test
    fun `returnTo with blank value redirects to root`() {
        val response = loginRequest(returnTo = "")
        assertEquals(Status.FOUND, response.status)
        val location = response.header("location") ?: ""
        assertEquals("/", location, "Blank returnTo should redirect to /")
    }

    @Test
    fun `failed login does not redirect regardless of returnTo`() {
        val response =
            app(
                Request(POST, "/auth/components/result?returnTo=/messages")
                    .header("content-type", "application/x-www-form-urlencoded")
                    .body("mode=sign-in&email=${testUser.email}&password=WRONG")
            )
        // Failed login returns a rendered HTML fragment, not a redirect
        assertTrue(response.status != Status.FOUND, "Failed login should not redirect, got status: ${response.status}")
    }

    @Test
    fun `login sets session cookie on success`() {
        val response = loginRequest(returnTo = "/")
        assertEquals(Status.FOUND, response.status)
        val setCookie = response.header("Set-Cookie") ?: ""
        assertTrue(
            setCookie.contains(WebContext.SESSION_COOKIE),
            "Set-Cookie should contain session cookie, got: $setCookie",
        )
    }
}
