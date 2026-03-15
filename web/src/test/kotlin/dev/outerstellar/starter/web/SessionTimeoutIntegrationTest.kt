package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.jooq.tables.references.USERS
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.security.User
import dev.outerstellar.starter.security.UserRole
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * Integration tests for session timeout enforcement.
 *
 * Covers:
 * - Active session (recent lastActivityAt) is not expired
 * - Expired bearer token (lastActivityAt > sessionTimeoutMinutes ago) returns 401 +
 *   X-Session-Expired header
 * - Expired session cookie redirects to /auth?expired=true on HTML routes
 * - Fresh user with null lastActivityAt is not expired
 */
class SessionTimeoutIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var activeUser: User
    private lateinit var expiredUser: User

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder)
        val pageFactory =
            WebPageFactory(repository, messageService, contactService, securityService)

        // User with recent activity (1 minute ago)
        activeUser =
            User(
                id = UUID.randomUUID(),
                username = "activeuser",
                email = "active@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )

        // User with expired session (activity > 30 minutes ago)
        expiredUser =
            User(
                id = UUID.randomUUID(),
                username = "expireduser",
                email = "expired@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )

        userRepository.save(activeUser)
        userRepository.save(expiredUser)

        // Set lastActivityAt to 2 hours ago using typed JOOQ API
        val twoHoursAgo = LocalDateTime.now(ZoneOffset.UTC).minusHours(2)
        val oneMinuteAgo = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1)

        testDsl
            .update(USERS)
            .set(USERS.LAST_ACTIVITY_AT, twoHoursAgo)
            .where(USERS.ID.eq(expiredUser.id))
            .execute()

        testDsl
            .update(USERS)
            .set(USERS.LAST_ACTIVITY_AT, oneMinuteAgo)
            .where(USERS.ID.eq(activeUser.id))
            .execute()

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

    // ---- Bearer token timeout ----

    @Test
    fun `expired bearer token returns 401 with X-Session-Expired header`() {
        val response =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${expiredUser.id}"))

        assertEquals(Status.UNAUTHORIZED, response.status)
        assertEquals(
            "true",
            response.header("X-Session-Expired"),
            "X-Session-Expired header must be set to true",
        )
    }

    @Test
    fun `active bearer token is not expired and accesses sync endpoint`() {
        val response =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${activeUser.id}"))

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `fresh user with null lastActivityAt is not expired`() {
        // freshUser has no lastActivityAt (null) — should not be expired (treated as "never set")
        val freshUser =
            User(
                id = UUID.randomUUID(),
                username = "freshuser",
                email = "fresh@test.com",
                passwordHash = "x".repeat(60),
                role = UserRole.USER,
            )
        userRepository.save(freshUser)

        val response =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${freshUser.id}"))

        assertEquals(Status.OK, response.status)
    }

    // ---- Session cookie timeout ----

    @Test
    fun `expired session cookie on HTML route redirects to auth with expired param`() {
        val response =
            app(
                Request(GET, "/")
                    .cookie(Cookie(WebContext.SESSION_COOKIE, expiredUser.id.toString()))
            )

        // Redirect to /auth?expired=true
        assertEquals(Status.FOUND, response.status, "Expired session should cause redirect")
        val location = response.header("location").orEmpty()
        assertTrue(
            location.contains("expired"),
            "Redirect location should indicate session expired, got: $location",
        )
    }

    @Test
    fun `active session cookie on HTML route is accepted`() {
        val response =
            app(
                Request(GET, "/")
                    .cookie(Cookie(WebContext.SESSION_COOKIE, activeUser.id.toString()))
            )

        assertEquals(Status.OK, response.status, "Active session should succeed")
    }

    @Test
    fun `expired bearer token response body mentions expiry`() {
        val response =
            app(Request(GET, "/api/v1/sync").header("Authorization", "Bearer ${expiredUser.id}"))

        val body = response.bodyString()
        assertTrue(
            body.contains("expired", ignoreCase = true) || body.contains("Session"),
            "Response body should mention expiry, got: $body",
        )
    }
}
