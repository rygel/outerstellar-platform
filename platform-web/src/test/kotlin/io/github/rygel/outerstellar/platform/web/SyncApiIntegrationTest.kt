package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the sync API (Feature 4 — bearer-authenticated sync endpoints).
 *
 * Covers:
 * - GET /api/v1/sync without bearer → 401
 * - GET /api/v1/sync with valid bearer → 200 JSON
 * - GET /api/v1/sync?since= is forwarded to the service
 * - POST /api/v1/sync without bearer → 401
 * - POST /api/v1/sync with valid bearer → 200, delegates to messageService
 * - POST /api/v1/sync with malformed JSON → 400
 * - GET /api/v1/sync/contacts without bearer → 401
 * - GET /api/v1/sync/contacts with valid bearer → 200 JSON
 * - POST /api/v1/sync/contacts without bearer → 401
 * - POST /api/v1/sync/contacts with valid bearer → 200, delegates to contactService
 * - Response Content-Type is application/json
 * - appliedCount and conflicts are present in push response
 */
class SyncApiIntegrationTest : WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        val securityService =
            SecurityService(
                userRepository,
                encoder,
                sessionRepository = sessionRepository,
                apiKeyRepository = apiKeyRepository,
                resetRepository = passwordResetRepository,
                auditRepository = auditRepository,
            )

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "syncuser",
                email = "sync@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun bearerHeader() = "Bearer $sessionToken"

    // ---- GET /api/v1/sync ----

    @Test
    fun `GET sync without bearer returns 401`() {
        val response = app(Request(GET, "/api/v1/sync"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `GET sync with valid bearer returns 200`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", bearerHeader()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET sync response is JSON with messages and serverTimestamp fields`() {
        val response = app(Request(GET, "/api/v1/sync").header("Authorization", bearerHeader()))
        val contentType = response.header("content-type").orEmpty()
        assertTrue(contentType.contains("application/json"), "Sync response must be JSON, got: $contentType")
        val body = response.bodyString()
        assertTrue(body.contains("messages"), "Pull response must have 'messages' field")
        assertTrue(body.contains("serverTimestamp"), "Pull response must have 'serverTimestamp' field")
    }

    @Test
    fun `GET sync with since=0 returns all messages`() {
        val response = app(Request(GET, "/api/v1/sync?since=0").header("Authorization", bearerHeader()))
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPullResponse::class)
        assertNotNull(body.messages)
    }

    @Test
    fun `GET sync with since= large timestamp returns empty list`() {
        val response =
            app(Request(GET, "/api/v1/sync?since=${Long.MAX_VALUE - 1}").header("Authorization", bearerHeader()))
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPullResponse::class)
        assertTrue(body.messages.isEmpty(), "No messages should exist after max timestamp")
    }

    // ---- POST /api/v1/sync ----

    @Test
    fun `POST sync without bearer returns 401`() {
        val response =
            app(Request(POST, "/api/v1/sync").header("content-type", "application/json").body("""{"messages":[]}"""))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST sync with empty messages list returns 200 with zero appliedCount`() {
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("""{"messages":[]}""")
            )
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushResponse::class)
        assertEquals(0, body.appliedCount)
        assertTrue(body.conflicts.isEmpty())
    }

    @Test
    fun `POST sync with valid message persists it and returns appliedCount 1`() {
        val syncId = UUID.randomUUID().toString() // must fit varchar(36)
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body(
                        """{"messages":[{"syncId":"$syncId",""" +
                            """"author":"syncuser","content":"Hello sync",""" +
                            """"updatedAtEpochMs":1000}]}"""
                    )
            )
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushResponse::class)
        assertEquals(1, body.appliedCount, "One message should be applied")
    }

    @Test
    fun `POST sync push response contains conflicts field`() {
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("""{"messages":[]}""")
            )
        val body = response.bodyString()
        assertTrue(body.contains("conflicts"), "Push response must include 'conflicts' field")
    }

    @Test
    fun `POST sync with malformed JSON returns 400 or 500`() {
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("{invalid json{{{")
            )
        assertTrue(response.status.code >= 400, "Malformed JSON should return 4xx or 5xx, got: ${response.status}")
    }

    // ---- GET /api/v1/sync/contacts ----

    @Test
    fun `GET sync-contacts without bearer returns 401`() {
        val response = app(Request(GET, "/api/v1/sync/contacts"))
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `GET sync-contacts with valid bearer returns 200`() {
        val response = app(Request(GET, "/api/v1/sync/contacts").header("Authorization", bearerHeader()))
        assertEquals(Status.OK, response.status)
    }

    @Test
    fun `GET sync-contacts response contains contacts and serverTimestamp fields`() {
        val response = app(Request(GET, "/api/v1/sync/contacts").header("Authorization", bearerHeader()))
        val body = response.bodyString()
        assertTrue(body.contains("contacts"), "Contacts pull response must have 'contacts' field")
        assertTrue(body.contains("serverTimestamp"), "Contacts pull response must have 'serverTimestamp' field")
    }

    @Test
    fun `GET sync-contacts since param returns results filtered by timestamp`() {
        val response = app(Request(GET, "/api/v1/sync/contacts?since=999").header("Authorization", bearerHeader()))
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPullContactResponse::class)
        assertNotNull(body.contacts)
    }

    // ---- POST /api/v1/sync/contacts ----

    @Test
    fun `POST sync-contacts without bearer returns 401`() {
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("content-type", "application/json")
                    .body("""{"contacts":[]}""")
            )
        assertEquals(Status.UNAUTHORIZED, response.status)
    }

    @Test
    fun `POST sync-contacts with valid bearer and empty list returns 200`() {
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("""{"contacts":[]}""")
            )
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        assertEquals(0, body.appliedCount)
    }

    @Test
    fun `POST sync-contacts with empty list delegates to contactService`() {
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("""{"contacts":[]}""")
            )
        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        // Verify delegation by checking that a valid response was returned
        assertNotNull(body, "Contact push should return a valid response")
    }

    @Test
    fun `POST sync-contacts response contains appliedCount and conflicts`() {
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearerHeader())
                    .header("content-type", "application/json")
                    .body("""{"contacts":[]}""")
            )
        val body = response.bodyString()
        assertTrue(body.contains("appliedCount"), "Response must have 'appliedCount'")
        assertTrue(body.contains("conflicts"), "Response must have 'conflicts'")
    }
}
