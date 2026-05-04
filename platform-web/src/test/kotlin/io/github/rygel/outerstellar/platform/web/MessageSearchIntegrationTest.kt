package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.security.SecurityService
import io.github.rygel.outerstellar.platform.security.User
import io.github.rygel.outerstellar.platform.security.UserRole
import io.github.rygel.outerstellar.platform.sync.SyncPullResponse
import io.github.rygel.outerstellar.platform.sync.SyncPushResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
 * Integration tests for message search via the sync API.
 *
 * Covers:
 * - GET /api/v1/sync returns all messages when no filter
 * - Pushed messages appear in subsequent pull
 * - Deleted messages do not appear in pull response
 * - Multiple messages with different content can be created
 * - Pull with since=0 returns all messages
 * - Pull with since=timestamp only returns newer messages
 */
class MessageSearchIntegrationTest : WebTest() {

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
                username = "searchuser",
                email = "search@test.com",
                passwordHash = encoder.encode(testPassword()),
                role = UserRole.USER,
            )
        userRepository.save(testUser)
        sessionToken = securityService.createSession(testUser.id)

        app = buildApp(securityService = securityService)
    }

    @AfterEach fun teardown() = cleanup()

    private fun bearer() = "Bearer $sessionToken"

    private fun pushMessage(
        syncId: String,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
        deleted: Boolean = false,
    ): org.http4k.core.Response =
        app(
            Request(POST, "/api/v1/sync")
                .header("Authorization", bearer())
                .header("content-type", "application/json")
                .body(
                    """{"messages":[{"syncId":"$syncId","author":"searchuser",""" +
                        """"content":"$content","updatedAtEpochMs":$timestamp,""" +
                        """"deleted":$deleted}]}"""
                )
        )

    private fun pullMessages(since: Long = 0L): SyncPullResponse {
        val response = app(Request(GET, "/api/v1/sync?since=$since").header("Authorization", bearer()))
        assertEquals(Status.OK, response.status)
        return Jackson.asA(response.bodyString(), SyncPullResponse::class)
    }

    @Test
    fun `pull returns empty list when no messages`() {
        val response = pullMessages()
        assertTrue(response.messages.isEmpty(), "No messages should be returned initially")
    }

    @Test
    fun `pushed message appears in pull response`() {
        val syncId = UUID.randomUUID().toString()
        pushMessage(syncId, "Hello search world", timestamp = 1000L)

        val response = pullMessages()
        val found = response.messages.any { it.syncId == syncId }
        assertTrue(found, "Pushed message should appear in pull response")
    }

    @Test
    fun `pushed message content is preserved in pull`() {
        val syncId = UUID.randomUUID().toString()
        val content = "Unique test content for search"
        pushMessage(syncId, content, timestamp = 1000L)

        val response = pullMessages()
        val message = response.messages.find { it.syncId == syncId }
        assertEquals(content, message?.content, "Message content should be preserved")
    }

    @Test
    fun `multiple pushed messages all appear in pull`() {
        val ids = (1..3).map { UUID.randomUUID().toString() }
        ids.forEachIndexed { i, syncId -> pushMessage(syncId, "Message content $i", timestamp = (1000L + i * 100)) }

        val response = pullMessages()
        ids.forEach { syncId ->
            assertTrue(response.messages.any { it.syncId == syncId }, "Message $syncId should appear in pull")
        }
    }

    @Test
    fun `pull with since timestamp only returns newer messages`() {
        val oldId = UUID.randomUUID().toString()
        val newId = UUID.randomUUID().toString()

        pushMessage(oldId, "Old message", timestamp = 1000L)
        pushMessage(newId, "New message", timestamp = 5000L)

        // Pull only messages newer than 2000ms
        val response = pullMessages(since = 2000L)
        val syncIds = response.messages.map { it.syncId }

        assertTrue(newId in syncIds, "Newer message should be included")
        assertFalse(oldId in syncIds, "Older message should be excluded by since filter")
    }

    @Test
    fun `deleted message is marked as deleted in pull response`() {
        val syncId = UUID.randomUUID().toString()
        pushMessage(syncId, "About to be deleted", timestamp = 1000L)
        pushMessage(syncId, "Deleted version", timestamp = 2000L, deleted = true)

        val response = pullMessages()
        val message = response.messages.find { it.syncId == syncId }
        assertTrue(message?.deleted == true, "Deleted message should have deleted=true in pull")
    }

    @Test
    fun `push returns appliedCount for each message applied`() {
        val syncId = UUID.randomUUID().toString()
        val pushResponse = pushMessage(syncId, "Count test", timestamp = 1000L)

        assertEquals(Status.OK, pushResponse.status)
        val body = Jackson.asA(pushResponse.bodyString(), SyncPushResponse::class)
        assertEquals(1, body.appliedCount, "One message should be applied")
    }

    @Test
    fun `pull since=0 returns all messages`() {
        val count = 3
        repeat(count) { i -> pushMessage(UUID.randomUUID().toString(), "Msg $i", timestamp = 1000L + i) }

        val response = pullMessages(since = 0L)
        assertEquals(count, response.messages.size, "Should return all $count messages")
    }
}
