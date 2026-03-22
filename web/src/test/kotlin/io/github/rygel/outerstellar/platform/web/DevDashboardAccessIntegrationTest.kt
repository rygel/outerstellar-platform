package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqAuditRepository
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for the developer dashboard at GET /admin/dev.
 *
 * Covers:
 * - Admin can access the dev dashboard when devDashboardEnabled=true
 * - Non-admin is forbidden from the dev dashboard
 * - Unauthenticated request is rejected
 * - Dashboard response contains expected metrics sections
 * - Dashboard is unavailable when devDashboardEnabled=false
 */
class DevDashboardAccessIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var appWithDashboardDisabled: HttpHandler
    private lateinit var adminUser: User
    private lateinit var regularUser: User

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val auditRepository = JooqAuditRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, encoder, auditRepository)
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "devdash_admin",
                email = "devdash_admin@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.ADMIN,
            )
        regularUser =
            User(
                id = UUID.randomUUID(),
                username = "devdash_user",
                email = "devdash_user@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(adminUser)
        userRepository.save(regularUser)

        val renderer = createRenderer()

        // testConfig already has devDashboardEnabled=true
        app =
            app(
                messageService,
                contactService,
                outbox,
                cache,
                renderer,
                pageFactory,
                testConfig,
                securityService,
                userRepository,
            )
                .http!!

        // A second app instance with the dashboard disabled
        appWithDashboardDisabled =
            app(
                messageService,
                contactService,
                outbox,
                cache,
                renderer,
                pageFactory,
                testConfig.copy(devDashboardEnabled = false),
                securityService,
                userRepository,
            )
                .http!!
    }

    @AfterEach fun teardown() = cleanup()

    private fun adminSession() = Cookie(WebContext.SESSION_COOKIE, adminUser.id.toString())

    private fun userSession() = Cookie(WebContext.SESSION_COOKIE, regularUser.id.toString())

    @Test
    fun `admin can access dev dashboard`() {
        val response = app(Request(GET, "/admin/dev").cookie(adminSession()))

        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `non-admin cannot access dev dashboard`() {
        val response = app(Request(GET, "/admin/dev").cookie(userSession()))

        assertTrue(response.status != Status.OK)
    }

    @Test
    fun `unauthenticated request to dev dashboard is rejected`() {
        val response = app(Request(GET, "/admin/dev"))

        assertTrue(response.status != Status.OK)
    }

    @Test
    fun `dev dashboard response contains outbox section`() {
        val response = app(Request(GET, "/admin/dev").cookie(adminSession()))

        val body = response.bodyString()
        assertTrue(body.contains("Outbox", ignoreCase = true) || body.contains("outbox", ignoreCase = true))
    }

    @Test
    fun `dev dashboard response is HTML`() {
        val response = app(Request(GET, "/admin/dev").cookie(adminSession()))

        assertTrue(response.header("content-type")?.contains("text/html") == true)
    }

    @Test
    fun `dev dashboard returns 404 when disabled`() {
        val response = appWithDashboardDisabled(Request(GET, "/admin/dev").cookie(adminSession()))

        assertEquals(Status.NOT_FOUND, response.status)
    }
}
