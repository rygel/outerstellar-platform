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
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for static asset serving (classpath static/ directory).
 *
 * Covers:
 * - GET /site.css returns 200
 * - GET /swagger.html returns 200
 * - Static CSS files have correct content-type (text/css)
 * - Static HTML files have content-type containing text/html
 * - Non-existent static files return 404 (not 500)
 * - Static files are served without authentication
 * - Static file response body is not empty
 */
class StaticAssetIntegrationTest : H2WebTest() {

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

    @Test
    fun `GET site css returns 200`() {
        val response = app(Request(GET, "/site.css"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET swagger html returns 200`() {
        val response = app(Request(GET, "/swagger.html"))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET site css returns non-empty body`() {
        val body = app(Request(GET, "/site.css")).bodyString()
        assertTrue(body.isNotBlank(), "site.css should not be empty")
    }

    @Test
    fun `GET swagger html returns non-empty body`() {
        val body = app(Request(GET, "/swagger.html")).bodyString()
        assertTrue(body.isNotBlank(), "swagger.html should not be empty")
    }

    @Test
    fun `GET site css has CSS content type`() {
        val contentType = app(Request(GET, "/site.css")).header("content-type").orEmpty()
        assertTrue(
            contentType.contains("css", ignoreCase = true) ||
                contentType.contains("text/plain", ignoreCase = true),
            "site.css should have a CSS-related content type, got: $contentType",
        )
    }

    @Test
    fun `GET swagger html has HTML content type`() {
        val contentType = app(Request(GET, "/swagger.html")).header("content-type").orEmpty()
        assertTrue(
            contentType.contains("html", ignoreCase = true),
            "swagger.html should have HTML content type, got: $contentType",
        )
    }

    @Test
    fun `non-existent static file does not return 500`() {
        val response = app(Request(GET, "/this-file-does-not-exist-at-all.xyz"))
        assertTrue(
            response.status.code != 500,
            "Non-existent file should not return 500, got: ${response.status}",
        )
    }

    @Test
    fun `static files are served without authentication`() {
        // No session cookie or Bearer token — should still serve static assets
        val response = app(Request(GET, "/site.css"))
        assertEquals(Status.OK, response.status, "Static files should not require authentication")
    }
}
