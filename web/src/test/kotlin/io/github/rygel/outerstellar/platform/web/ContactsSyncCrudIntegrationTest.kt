package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqContactRepository
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for contact sync CRUD — push contacts and pull them back via real DB.
 *
 * Covers:
 * - POST /api/v1/sync/contacts with a valid contact returns 200 and appliedCount 1
 * - GET /api/v1/sync/contacts after push returns the pushed contact
 * - Pushed contact has correct syncId in pull response
 * - Second push with same syncId and newer timestamp updates the contact
 * - Push with older timestamp when server has newer version produces a conflict
 * - Empty push returns appliedCount 0 and empty contacts list
 * - GET /api/v1/sync/contacts?since= filters by timestamp
 */
class ContactsSyncCrudIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

    @BeforeEach
    fun setupTest() {
        val encoder = BCryptPasswordEncoder(logRounds = 4)
        val userRepository = JooqUserRepository(testDsl)
        val repository = JooqMessageRepository(testDsl)
        val contactRepository = JooqContactRepository(testDsl)
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        val messageService = MessageService(repository, outbox, txManager, cache)
        val contactService = ContactService(contactRepository)
        val securityService =
            SecurityService(userRepository, encoder, sessionRepository = JooqSessionRepository(testDsl))
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "contactsyncuser",
                email = "contactsync@test.com",
                passwordHash = encoder.encode("pass"),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

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

    private fun bearer() = "Bearer $sessionToken"

    private fun contactJson(syncId: String, name: String, timestamp: Long = 1000L) =
        """
        {
          "contacts": [{
            "syncId": "$syncId",
            "name": "$name",
            "emails": ["test@example.com"],
            "phones": [],
            "socialMedia": [],
            "company": "",
            "companyAddress": "",
            "department": "",
            "updatedAtEpochMs": $timestamp,
            "deleted": false
          }]
        }
        """
            .trimIndent()

    @Test
    fun `POST sync-contacts with valid contact returns 200 and appliedCount 1`() {
        val syncId = UUID.randomUUID().toString()
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(contactJson(syncId, "Alice Smith"))
            )

        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        assertEquals(1, body.appliedCount, "One contact should be applied")
        assertTrue(body.conflicts.isEmpty(), "No conflicts expected")
    }

    @Test
    fun `GET sync-contacts after push returns the contact`() {
        val syncId = UUID.randomUUID().toString()

        // Push contact
        app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(contactJson(syncId, "Bob Jones"))
        )

        // Pull contacts
        val response = app(Request(GET, "/api/v1/sync/contacts?since=0").header("Authorization", bearer()))
        assertEquals(Status.OK, response.status)

        val body = Jackson.asA(response.bodyString(), SyncPullContactResponse::class)
        val found = body.contacts.any { it.syncId == syncId }
        assertTrue(found, "Pushed contact should be returned in pull response")
    }

    @Test
    fun `pushed contact has correct syncId in pull response`() {
        val syncId = UUID.randomUUID().toString()

        app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(contactJson(syncId, "Charlie Brown"))
        )

        val response = app(Request(GET, "/api/v1/sync/contacts?since=0").header("Authorization", bearer()))
        val body = Jackson.asA(response.bodyString(), SyncPullContactResponse::class)
        val contact = body.contacts.find { it.syncId == syncId }
        assertEquals(syncId, contact?.syncId, "SyncId should match pushed value")
    }

    @Test
    fun `push with older timestamp when server has newer version produces conflict`() {
        val syncId = UUID.randomUUID().toString()

        // First push with timestamp 2000
        app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(contactJson(syncId, "Diana Prince", timestamp = 2000L))
        )

        // Second push with OLDER timestamp 1000 — should produce conflict
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(contactJson(syncId, "Diana Prince UPDATED", timestamp = 1000L))
            )

        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        assertEquals(0, body.appliedCount, "Older version should not be applied")
        assertEquals(1, body.conflicts.size, "One conflict expected")
        assertEquals(syncId, body.conflicts[0].syncId, "Conflict should reference the syncId")
    }

    @Test
    fun `push with newer timestamp updates the contact`() {
        val syncId = UUID.randomUUID().toString()

        // First push
        app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(contactJson(syncId, "Eve Adams", timestamp = 1000L))
        )

        // Second push with NEWER timestamp — should be applied
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(contactJson(syncId, "Eve Adams UPDATED", timestamp = 3000L))
            )

        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        assertEquals(1, body.appliedCount, "Newer version should be applied")
        assertTrue(body.conflicts.isEmpty())
    }

    @Test
    fun `empty push returns appliedCount 0`() {
        val response =
            app(
                Request(POST, "/api/v1/sync/contacts")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body("""{"contacts":[]}""")
            )

        assertEquals(Status.OK, response.status)
        val body = Jackson.asA(response.bodyString(), SyncPushContactResponse::class)
        assertEquals(0, body.appliedCount)
        assertTrue(body.conflicts.isEmpty())
    }

    @Test
    fun `GET sync-contacts with future since timestamp returns empty list`() {
        val syncId = UUID.randomUUID().toString()

        // Push contact with timestamp 1000
        app(
            Request(POST, "/api/v1/sync/contacts")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(contactJson(syncId, "Frank Castle", timestamp = 1000L))
        )

        // Pull with since=Long.MAX_VALUE — should be empty
        val response =
            app(Request(GET, "/api/v1/sync/contacts?since=${Long.MAX_VALUE - 1}").header("Authorization", bearer()))
        val body = Jackson.asA(response.bodyString(), SyncPullContactResponse::class)
        assertTrue(body.contacts.isEmpty(), "No contacts should be returned with future timestamp")
    }
}
