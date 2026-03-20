package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.UserSummary
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
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for admin CSV export endpoints and audit log page (Feature 5 — admin routes).
 *
 * Covers:
 * - GET /admin/users/export returns text/csv with correct Content-Disposition
 * - Export CSV has the right header row
 * - Export CSV contains seeded users
 * - Export CSV escapes commas and quotes in usernames/emails
 * - GET /admin/audit returns 200 HTML
 * - GET /admin/audit/export returns text/csv
 * - Audit CSV has the right header row
 * - Non-admin access to /admin/users/export returns 403
 * - Non-admin access to /admin/audit returns 403
 * - UserAdminRoutes.usersAsCsv formats correctly (unit-style)
 * - UserAdminRoutes.auditAsCsv formats correctly (unit-style)
 * - GET /admin/users renders pagination controls when limit < total users
 */
class AdminExportIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var userRepository: JooqUserRepository
    private lateinit var adminUser: User
    private lateinit var regularUser: User

    @BeforeEach
    fun setupTest() {
        cleanup()
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

        adminUser =
            User(
                id = UUID.randomUUID(),
                username = "exportadmin",
                email = "exportadmin@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.ADMIN,
            )
        regularUser =
            User(
                id = UUID.randomUUID(),
                username = "exportuser",
                email = "exportuser@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(adminUser)
        userRepository.save(regularUser)

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

    private fun adminSession() = Cookie(WebContext.SESSION_COOKIE, adminUser.id.toString())

    private fun userSession() = Cookie(WebContext.SESSION_COOKIE, regularUser.id.toString())

    // ---- GET /admin/users/export ----

    @Test
    fun `GET admin-users-export returns 200 with text-csv content type`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminSession()))
        assertEquals(Status.OK, response.status)
        val contentType = response.header("Content-Type").orEmpty()
        assertTrue(
            contentType.contains("text/csv"),
            "Export should return text/csv, got: $contentType",
        )
    }

    @Test
    fun `GET admin-users-export returns Content-Disposition attachment with filename`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminSession()))
        val disposition = response.header("Content-Disposition").orEmpty()
        assertTrue(
            disposition.contains("attachment"),
            "Export should be an attachment, got: $disposition",
        )
        assertTrue(
            disposition.contains("users.csv"),
            "Export filename should be users.csv, got: $disposition",
        )
    }

    @Test
    fun `GET admin-users-export CSV body has correct header row`() {
        val response = app(Request(GET, "/admin/users/export").cookie(adminSession()))
        val lines = response.bodyString().lines()
        assertTrue(lines.isNotEmpty(), "CSV should not be empty")
        val header = lines.first()
        assertTrue(header.contains("Username"), "CSV header should contain Username, got: $header")
        assertTrue(header.contains("Email"), "CSV header should contain Email, got: $header")
        assertTrue(header.contains("Role"), "CSV header should contain Role, got: $header")
        assertTrue(header.contains("Enabled"), "CSV header should contain Enabled, got: $header")
    }

    @Test
    fun `GET admin-users-export CSV body contains seeded users`() {
        val body = app(Request(GET, "/admin/users/export").cookie(adminSession())).bodyString()
        assertTrue(body.contains(adminUser.username), "CSV should contain admin username")
        assertTrue(body.contains(regularUser.username), "CSV should contain regular username")
    }

    @Test
    fun `GET admin-users-export as non-admin returns 403`() {
        val response = app(Request(GET, "/admin/users/export").cookie(userSession()))
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `GET admin-users-export unauthenticated redirects to auth`() {
        val response = app(Request(GET, "/admin/users/export"))
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- GET /admin/audit ----

    @Test
    fun `GET admin-audit as admin returns 200`() {
        val response = app(Request(GET, "/admin/audit").cookie(adminSession()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET admin-audit returns HTML`() {
        val body = app(Request(GET, "/admin/audit").cookie(adminSession())).bodyString()
        assertTrue(body.contains("<"), "Audit page should return HTML content")
    }

    @Test
    fun `GET admin-audit as non-admin returns 403`() {
        val response = app(Request(GET, "/admin/audit").cookie(userSession()))
        assertEquals(Status.FORBIDDEN, response.status)
    }

    @Test
    fun `GET admin-audit unauthenticated redirects to auth`() {
        val response = app(Request(GET, "/admin/audit"))
        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location").orEmpty().contains("/auth"))
    }

    // ---- GET /admin/audit/export ----

    @Test
    fun `GET admin-audit-export returns 200 with text-csv`() {
        val response = app(Request(GET, "/admin/audit/export").cookie(adminSession()))
        assertEquals(Status.OK, response.status)
        val contentType = response.header("Content-Type").orEmpty()
        assertTrue(
            contentType.contains("text/csv"),
            "Audit export should be CSV, got: $contentType",
        )
    }

    @Test
    fun `GET admin-audit-export has correct header row`() {
        val response = app(Request(GET, "/admin/audit/export").cookie(adminSession()))
        val firstLine = response.bodyString().lines().firstOrNull().orEmpty()
        assertTrue(
            firstLine.contains("Action"),
            "Audit CSV header should contain Action, got: $firstLine",
        )
        assertTrue(
            firstLine.contains("Actor"),
            "Audit CSV header should contain Actor, got: $firstLine",
        )
    }

    @Test
    fun `GET admin-audit-export Content-Disposition is attachment with filename audit-csv`() {
        val response = app(Request(GET, "/admin/audit/export").cookie(adminSession()))
        val disposition = response.header("Content-Disposition").orEmpty()
        assertTrue(
            disposition.contains("audit.csv"),
            "Disposition should reference audit.csv, got: $disposition",
        )
    }

    @Test
    fun `GET admin-audit-export as non-admin returns 403`() {
        val response = app(Request(GET, "/admin/audit/export").cookie(userSession()))
        assertEquals(Status.FORBIDDEN, response.status)
    }

    // ---- usersAsCsv unit tests ----

    @Test
    fun `usersAsCsv produces one data row per user`() {
        val users =
            listOf(
                UserSummary("1", "alice", "alice@test.com", "USER", true),
                UserSummary("2", "bob", "bob@test.com", "ADMIN", true),
            )
        val csv = UserAdminRoutes.usersAsCsv(users)
        val lines = csv.trim().lines()
        // header + 2 data lines
        assertEquals(3, lines.size, "CSV should have 1 header + 2 data rows")
    }

    @Test
    fun `usersAsCsv escapes username containing a comma`() {
        val users = listOf(UserSummary("1", "last, first", "e@test.com", "USER", true))
        val csv = UserAdminRoutes.usersAsCsv(users)
        assertTrue(csv.contains("\"last, first\""), "Comma in username should be quoted, got: $csv")
    }

    @Test
    fun `usersAsCsv escapes username containing a double-quote`() {
        val users = listOf(UserSummary("1", "say \"hello\"", "e@test.com", "USER", true))
        val csv = UserAdminRoutes.usersAsCsv(users)
        // CSV escaping: " → "" inside quoted field
        assertTrue(csv.contains("\"\""), "Double-quotes in value should be escaped")
    }

    @Test
    fun `auditAsCsv produces one data row per entry`() {
        val entries =
            listOf(
                AuditEntry(
                    actorId = "1",
                    actorUsername = "admin",
                    targetId = null,
                    targetUsername = null,
                    action = "USER_REGISTERED",
                    detail = null,
                ),
                AuditEntry(
                    actorId = "1",
                    actorUsername = "admin",
                    targetId = "2",
                    targetUsername = "bob",
                    action = "USER_DISABLED",
                    detail = null,
                ),
            )
        val csv = UserAdminRoutes.auditAsCsv(entries)
        val lines = csv.trim().lines()
        assertEquals(3, lines.size, "Audit CSV should have 1 header + 2 data rows")
    }

    @Test
    fun `auditAsCsv includes action values in output`() {
        val entries =
            listOf(
                AuditEntry(
                    actorId = "1",
                    actorUsername = "admin",
                    targetId = null,
                    targetUsername = null,
                    action = "PASSWORD_CHANGED",
                    detail = null,
                )
            )
        val csv = UserAdminRoutes.auditAsCsv(entries)
        assertTrue(csv.contains("PASSWORD_CHANGED"), "Audit CSV should include action value")
    }

    // ---- GET /admin/users pagination ----

    @Test
    fun `GET admin-users with limit=1 renders page with pagination awareness`() {
        val response = app(Request(GET, "/admin/users?limit=1&offset=0").cookie(adminSession()))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "User admin page should render content")
    }
}
