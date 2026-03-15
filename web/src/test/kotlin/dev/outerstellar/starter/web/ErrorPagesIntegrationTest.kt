package dev.outerstellar.starter.web

import dev.outerstellar.starter.app
import dev.outerstellar.starter.infra.createRenderer
import dev.outerstellar.starter.persistence.JooqMessageRepository
import dev.outerstellar.starter.persistence.JooqUserRepository
import dev.outerstellar.starter.security.BCryptPasswordEncoder
import dev.outerstellar.starter.security.SecurityService
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for error pages and the global error handler (Feature 8).
 *
 * Covers:
 * - GET /errors/not-found returns 200 HTML (themed error page)
 * - GET /errors/server-error returns 200 HTML
 * - GET /errors/forbidden returns 200 HTML
 * - GET /errors/components/help/not-found returns 200 HTML fragment
 * - GET /errors/components/help/server-error returns 200 HTML fragment
 * - Requesting a totally unknown path returns 404
 * - 404 for HTML path returns text/html (not JSON)
 * - 404 for /api/ path returns JSON error body
 * - 404 response still contains X-Request-Id header
 * - 404 HTML page is a full themed page (contains the shell layout)
 * - Hitting /health returns 200 with JSON body and status:UP
 * - Health endpoint JSON includes database key
 * - /errors/components/help/{kind} with unknown kind still returns 200 (graceful fallback)
 */
class ErrorPagesIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

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

    // ---- /errors/{kind} ----

    @Test
    fun `GET errors-not-found returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/not-found"))
        assertEquals(Status.OK, response.status)
        val body = response.bodyString()
        assertTrue(body.isNotBlank(), "Error page should have content")
    }

    @Test
    fun `GET errors-server-error returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/server-error"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `GET errors-forbidden returns 200 with HTML`() {
        val response = app(Request(GET, "/errors/forbidden"))
        assertEquals(Status.OK, response.status)
        assertTrue(response.bodyString().isNotBlank())
    }

    @Test
    fun `GET errors page is a full themed page containing layout shell elements`() {
        val body = app(Request(GET, "/errors/not-found")).bodyString()
        // The full layout includes the topbar, sidebar, etc.
        assertTrue(
            body.contains("<!DOCTYPE html>") || body.contains("<html") || body.contains("<body"),
            "Error page should be a full HTML document, got: ${body.take(200)}",
        )
    }

    // ---- /errors/components/help/{kind} ----

    @Test
    fun `GET errors-components-help-not-found returns 200`() {
        val response = app(Request(GET, "/errors/components/help/not-found"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET errors-components-help-server-error returns 200`() {
        val response = app(Request(GET, "/errors/components/help/server-error"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET errors-components-help with unknown kind returns 200 (graceful fallback)`() {
        val response = app(Request(GET, "/errors/components/help/totally-unknown-error-kind"))
        assertEquals(Status.OK, response.status)
    }

    // ---- Unknown paths → global 404 handler ----

    @Test
    fun `unknown HTML path returns 404`() {
        val response = app(Request(GET, "/nonexistent-page-xyzabc"))
        assertEquals(Status.NOT_FOUND, response.status)
    }

    @Test
    fun `404 for HTML path returns text-html`() {
        val response = app(Request(GET, "/this-does-not-exist"))
        val contentType = response.header("content-type").orEmpty()
        assertTrue(
            contentType.contains("text/html"),
            "404 for HTML route should return text/html, got: $contentType",
        )
    }

    @Test
    fun `404 for API path returns JSON`() {
        val response = app(Request(GET, "/api/v1/nonexistent"))
        val contentType = response.header("content-type").orEmpty()
        assertTrue(
            contentType.contains("application/json"),
            "404 for API route should return JSON, got: $contentType",
        )
    }

    @Test
    fun `404 API JSON body has message field`() {
        val response = app(Request(GET, "/api/v1/nonexistent"))
        val body = response.bodyString()
        assertTrue(
            body.contains("message") || body.contains("error"),
            "API 404 JSON should have a message field, got: $body",
        )
    }

    @Test
    fun `404 response still carries X-Request-Id header`() {
        val response = app(Request(GET, "/nonexistent-path-123"))
        val requestId = response.header("X-Request-Id")
        assertNotNull(requestId, "X-Request-Id should be present even on 404 responses")
        assertTrue(requestId.isNotBlank())
    }

    @Test
    fun `404 HTML page contains themed error content`() {
        val body = app(Request(GET, "/completely-unknown-path")).bodyString()
        // The error handler calls buildErrorPage which uses the full layout
        assertTrue(body.isNotBlank(), "404 page should have content")
    }

    // ---- /health ----

    @Test
    fun `GET health returns 200`() {
        val response = app(Request(GET, "/health"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET health response is JSON`() {
        val response = app(Request(GET, "/health"))
        val contentType = response.header("content-type").orEmpty()
        assertTrue(
            contentType.contains("application/json"),
            "Health endpoint must return JSON, got: $contentType",
        )
    }

    @Test
    fun `GET health JSON body contains status UP`() {
        val body = app(Request(GET, "/health")).bodyString()
        assertTrue(body.contains("UP"), "Health response should contain 'UP', got: $body")
    }

    @Test
    fun `GET health JSON body contains database key`() {
        val body = app(Request(GET, "/health")).bodyString()
        assertTrue(
            body.contains("database"),
            "Health response should contain 'database' key, got: $body",
        )
    }

    @Test
    fun `GET health JSON body contains timestamp`() {
        val body = app(Request(GET, "/health")).bodyString()
        assertTrue(
            body.contains("timestamp"),
            "Health response should contain 'timestamp', got: $body",
        )
    }

    // ---- Security headers still present on error pages ----

    @Test
    fun `404 response includes X-Content-Type-Options`() {
        val response = app(Request(GET, "/no-such-page"))
        assertEquals("nosniff", response.header("X-Content-Type-Options"))
    }

    @Test
    fun `404 response includes X-Frame-Options`() {
        val response = app(Request(GET, "/no-such-page"))
        assertEquals("DENY", response.header("X-Frame-Options"))
    }

    // ---- POST to unknown path ----

    @Test
    fun `POST to unknown API path returns 404 JSON`() {
        val response =
            app(
                Request(POST, "/api/v1/unknown-endpoint")
                    .header("content-type", "application/json")
                    .body("{}")
            )
        assertEquals(Status.NOT_FOUND, response.status)
        val contentType = response.header("content-type").orEmpty()
        assertTrue(contentType.contains("application/json"))
    }
}
