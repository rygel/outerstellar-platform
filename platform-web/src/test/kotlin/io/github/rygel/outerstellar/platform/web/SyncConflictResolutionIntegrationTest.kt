package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.app
import io.github.rygel.outerstellar.platform.infra.createRenderer
import io.github.rygel.outerstellar.platform.persistence.JooqMessageRepository
import io.github.rygel.outerstellar.platform.persistence.JooqSessionRepository
import io.github.rygel.outerstellar.platform.persistence.JooqUserRepository
import io.github.rygel.outerstellar.platform.security.BCryptPasswordEncoder
import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import io.mockk.mockk
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.http4k.core.HttpHandler
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.format.Jackson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for sync push conflict detection.
 *
 * Covers:
 * - Pushing the same syncId twice with a newer timestamp on second push succeeds (no conflict)
 * - Pushing the same syncId with an OLDER timestamp produces a conflict
 * - Conflict response contains the syncId of the conflicting message
 * - Conflict response includes the server's version (serverMessage)
 * - Non-conflicting messages in the same batch are still applied
 * - Multiple conflicts in a single push are all reported
 * - appliedCount reflects only successfully applied messages
 */
class SyncConflictResolutionIntegrationTest : H2WebTest() {

    private lateinit var app: HttpHandler
    private lateinit var testUser: User
    private lateinit var sessionToken: String

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
        val securityService =
            SecurityService(userRepository, encoder, sessionRepository = JooqSessionRepository(testDsl))
        val pageFactory = WebPageFactory(repository, messageService, contactService, securityService)

        testUser =
            User(
                id = UUID.randomUUID(),
                username = "conflictuser",
                email = "conflict@test.com",
                passwordHash = encoder.encode(testPassword()),
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

    private fun pushMessage(syncId: String, content: String, timestamp: Long): SyncPushResponse {
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(
                        """{"messages":[{"syncId":"$syncId",""" +
                            """"author":"conflictuser","content":"$content",""" +
                            """"updatedAtEpochMs":$timestamp}]}"""
                    )
            )
        assertEquals(Status.OK, response.status)
        return Jackson.asA(response.bodyString(), SyncPushResponse::class)
    }

    @Test
    fun `first push of message is applied with no conflicts`() {
        val syncId = UUID.randomUUID().toString()
        val result = pushMessage(syncId, "Hello world", 1000L)

        assertEquals(1, result.appliedCount, "First push should be applied")
        assertTrue(result.conflicts.isEmpty(), "No conflicts on first push")
    }

    @Test
    fun `pushing same syncId with older timestamp produces a conflict`() {
        val syncId = UUID.randomUUID().toString()

        // First push — timestamp 2000 (becomes the server's version)
        pushMessage(syncId, "Server version", 2000L)

        // Second push — OLDER timestamp 1000 → conflict
        val result = pushMessage(syncId, "Stale client version", 1000L)

        assertEquals(0, result.appliedCount, "Stale message should not be applied")
        assertEquals(1, result.conflicts.size, "One conflict expected")
    }

    @Test
    fun `conflict response references the conflicting syncId`() {
        val syncId = UUID.randomUUID().toString()

        pushMessage(syncId, "Server version", 2000L)
        val result = pushMessage(syncId, "Stale version", 500L)

        assertEquals(syncId, result.conflicts[0].syncId, "Conflict syncId must match pushed message")
    }

    @Test
    fun `conflict response includes server version of the message`() {
        val syncId = UUID.randomUUID().toString()

        pushMessage(syncId, "Original server content", 2000L)
        val result = pushMessage(syncId, "Stale content", 500L)

        assertNotNull(result.conflicts[0].serverMessage, "Conflict must include server version")
    }

    @Test
    fun `pushing same syncId with newer timestamp succeeds and updates`() {
        val syncId = UUID.randomUUID().toString()

        // First push — timestamp 1000
        pushMessage(syncId, "Old content", 1000L)

        // Second push — NEWER timestamp 3000 → applied, no conflict
        val result = pushMessage(syncId, "Updated content", 3000L)

        assertEquals(1, result.appliedCount, "Newer version should be applied")
        assertTrue(result.conflicts.isEmpty(), "No conflicts for newer timestamp")
    }

    @Test
    fun `batch with one conflicting and one new message reports partial success`() {
        val conflictSyncId = UUID.randomUUID().toString()
        val newSyncId = UUID.randomUUID().toString()

        // Establish server version for conflict
        pushMessage(conflictSyncId, "Server version", 2000L)

        // Push batch: one conflict + one new
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(
                        """
                        {"messages":[
                          {"syncId":"$conflictSyncId","author":"conflictuser","content":"Stale","updatedAtEpochMs":500},
                          {"syncId":"$newSyncId","author":"conflictuser","content":"Brand new","updatedAtEpochMs":1000}
                        ]}
                        """
                            .trimIndent()
                    )
            )

        val body = Jackson.asA(response.bodyString(), SyncPushResponse::class)
        assertEquals(1, body.appliedCount, "One new message should be applied")
        assertEquals(1, body.conflicts.size, "One conflict expected")
        assertEquals(conflictSyncId, body.conflicts[0].syncId)
    }

    @Test
    fun `multiple conflicts in one push are all reported`() {
        val syncId1 = UUID.randomUUID().toString()
        val syncId2 = UUID.randomUUID().toString()

        // Establish server versions
        pushMessage(syncId1, "Server 1", 2000L)
        pushMessage(syncId2, "Server 2", 3000L)

        // Push both with stale timestamps
        val response =
            app(
                Request(POST, "/api/v1/sync")
                    .header("Authorization", bearer())
                    .header("content-type", "application/json")
                    .body(
                        """
                        {"messages":[
                          {"syncId":"$syncId1","author":"conflictuser","content":"Stale 1","updatedAtEpochMs":500},
                          {"syncId":"$syncId2","author":"conflictuser","content":"Stale 2","updatedAtEpochMs":500}
                        ]}
                        """
                            .trimIndent()
                    )
            )

        val body = Jackson.asA(response.bodyString(), SyncPushResponse::class)
        assertEquals(0, body.appliedCount)
        assertEquals(2, body.conflicts.size, "Both conflicts should be reported")
    }

    companion object {
        private fun assertNotNull(value: Any?, message: String) {
            assertTrue(value != null, message)
        }
    }
}
