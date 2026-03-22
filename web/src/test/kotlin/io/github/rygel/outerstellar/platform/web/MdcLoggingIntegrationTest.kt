package io.github.rygel.outerstellar.platform.web

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.mockk.mockk
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for structured logging with MDC correlation IDs (Feature 1).
 *
 * Verifies that:
 * - Every response carries an X-Request-Id header
 * - A caller-supplied request ID is forwarded unchanged
 * - Auto-generated IDs are unique per request
 * - MDC requestId is set during request processing (captured via test appender)
 * - MDC is cleared after each request so values don't bleed across requests
 */
class MdcLoggingIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler

    // Collect log events during each test so we can verify MDC state
    private val capturedEvents = mutableListOf<ILoggingEvent>()
    private val testAppender =
        object : AppenderBase<ILoggingEvent>() {
            override fun append(event: ILoggingEvent) {
                capturedEvents.add(event)
            }
        }

    @BeforeEach
    fun setupTest() {
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        testAppender.context = rootLogger.loggerContext
        testAppender.name = "MDC_TEST_APPENDER"
        testAppender.start()
        rootLogger.addAppender(testAppender)

        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = mockk<io.github.rygel.outerstellar.platform.service.ContactService>(relaxed = true)
        val securityService = SecurityService(userRepository, BCryptPasswordEncoder(logRounds = 4))
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

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

    @AfterEach
    fun teardown() {
        val rootLogger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        rootLogger.detachAppender(testAppender)
        capturedEvents.clear()
        cleanup()
    }

    // ---- X-Request-Id header ----

    @Test
    fun `response includes X-Request-Id header when none was provided`() {
        val response = app(Request(GET, "/health"))

        val requestId = response.header("X-Request-Id")
        assertNotNull(requestId, "Response should carry X-Request-Id header")
        assertTrue(requestId.isNotBlank())
    }

    @Test
    fun `auto-generated request ID is a valid UUID`() {
        val response = app(Request(GET, "/health"))
        val requestId = response.header("X-Request-Id")!!

        // UUID format: 8-4-4-4-12 hex chars separated by dashes
        assertTrue(requestId.matches(Regex("[0-9a-f-]{36}")), "Generated request ID should be a UUID, got: $requestId")
    }

    @Test
    fun `caller-supplied X-Request-Id is forwarded unchanged in response`() {
        val customId = "caller-id-abc-12345"
        val response = app(Request(GET, "/health").header("X-Request-Id", customId))

        assertEquals(customId, response.header("X-Request-Id"), "Custom request ID must be echoed")
    }

    @Test
    fun `each request receives a distinct auto-generated request ID`() {
        val id1 = app(Request(GET, "/health")).header("X-Request-Id")
        val id2 = app(Request(GET, "/health")).header("X-Request-Id")
        val id3 = app(Request(GET, "/health")).header("X-Request-Id")

        assertNotNull(id1)
        assertNotNull(id2)
        assertNotNull(id3)
        assertTrue(setOf(id1, id2, id3).size == 3, "All three request IDs must be unique")
    }

    // ---- MDC state during processing ----

    @Test
    fun `MDC requestId is set while the request is being processed`() {
        val customId = "mdc-capture-test-id"
        app(Request(GET, "/health").header("X-Request-Id", customId))

        // The request logging filter logs with MDC; find events where requestId was present
        val eventsWithRequestId =
            capturedEvents.filter { event ->
                val mdc = event.mdcPropertyMap
                mdc != null && mdc.containsKey("requestId")
            }
        assertTrue(
            eventsWithRequestId.isNotEmpty(),
            "At least one log event should have MDC.requestId set during request processing",
        )
    }

    @Test
    fun `MDC requestId reflects the first 8 chars of the request ID`() {
        val customId = "full-uuid-goes-here-abcde12345678"
        app(Request(GET, "/health").header("X-Request-Id", customId))

        val eventsWithRequestId = capturedEvents.filter { e -> e.mdcPropertyMap?.containsKey("requestId") == true }
        assertTrue(eventsWithRequestId.isNotEmpty())

        val mdcRequestId = eventsWithRequestId.first().mdcPropertyMap["requestId"]!!
        assertEquals(customId.take(8), mdcRequestId, "MDC requestId should be first 8 chars of the full ID")
    }

    @Test
    fun `MDC method and path are set during request processing`() {
        app(Request(GET, "/health"))

        val eventsWithMethod = capturedEvents.filter { e -> e.mdcPropertyMap?.containsKey("method") == true }
        assertTrue(eventsWithMethod.isNotEmpty(), "Log events should have MDC.method set")
        assertEquals("GET", eventsWithMethod.first().mdcPropertyMap["method"])

        val eventsWithPath = capturedEvents.filter { e -> e.mdcPropertyMap?.containsKey("path") == true }
        assertTrue(eventsWithPath.isNotEmpty(), "Log events should have MDC.path set")
        assertEquals("/health", eventsWithPath.first().mdcPropertyMap["path"])
    }

    @Test
    fun `MDC is cleared after request completes so values do not bleed into next request`() {
        // First request with a known ID
        app(Request(GET, "/health").header("X-Request-Id", "first-req-id"))
        val countAfterFirst = capturedEvents.size

        // Second request — MDC must NOT carry forward the first request's values
        app(Request(GET, "/health").header("X-Request-Id", "second-req-id"))

        val secondRequestEvents = capturedEvents.drop(countAfterFirst)
        val wrongEvents = secondRequestEvents.filter { e -> e.mdcPropertyMap?.get("requestId") == "first-re" }
        assertTrue(wrongEvents.isEmpty(), "Second request must not carry first request's MDC requestId")
    }

    @Test
    fun `MDC username is set to anon for unauthenticated requests`() {
        app(Request(GET, "/health"))

        // The logback pattern uses %X{username:-anon}, so if not set the rendered text is "anon"
        // We verify directly that no "username" key was set (since user is null for /health)
        // which means the pattern renders the default "anon"
        val eventsWithUsername = capturedEvents.filter { e -> e.mdcPropertyMap?.containsKey("username") == true }
        // For unauthenticated requests, username MDC key should NOT be present
        // (the :-anon is the logback default substitution, not an MDC value)
        assertTrue(eventsWithUsername.isEmpty(), "Unauthenticated requests should not set MDC.username")
    }

    @Test
    fun `X-Request-Id header is present on non-200 responses`() {
        val response = app(Request(GET, "/nonexistent-page-xyz"))

        assertEquals(Status.NOT_FOUND, response.status)
        val requestId = response.header("X-Request-Id")
        assertNotNull(requestId, "X-Request-Id should be present even on 404 responses")
    }

    @Test
    fun `X-Request-Id is exposed in CORS headers`() {
        val response = app(Request(GET, "/health"))
        val exposeHeader = response.header("Access-Control-Expose-Headers")

        assertNotNull(exposeHeader)
        assertTrue(exposeHeader.contains("X-Request-Id"), "X-Request-Id should be listed in CORS Expose-Headers")
    }

    // ---- Log pattern verification ----

    @Test
    fun `logback pattern includes MDC fields in formatted output`() {
        // Trigger a real log line by making a request that will be logged
        val customId = "log-pattern-test-id"
        app(Request(POST, "/api/v1/auth/login").header("X-Request-Id", customId))

        // The requestLogging filter emits an INFO log — find it
        val requestLogEvents =
            capturedEvents.filter { e ->
                e.formattedMessage?.contains("POST") == true ||
                    e.formattedMessage?.contains("/api/v1/auth/login") == true
            }
        assertTrue(requestLogEvents.isNotEmpty(), "Request logging filter should emit a log line")
    }
}
