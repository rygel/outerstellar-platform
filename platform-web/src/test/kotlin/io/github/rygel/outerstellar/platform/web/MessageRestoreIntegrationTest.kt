package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.service.MessageService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.body.form
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Integration tests for the message restore flow.
 *
 * Covers:
 * - Restoring a soft-deleted message redirects to the trash page
 * - After restore the message disappears from trash
 * - After restore the message reappears on the home page
 * - Restoring an unknown syncId is graceful (no error)
 */
class MessageRestoreIntegrationTest : H2WebTest() {

    private lateinit var messageService: MessageService

    @BeforeEach
    fun setupTest() {
        cleanup()
        val outbox = StubOutboxRepository()
        val cache = StubMessageCache()
        val txManager = StubTransactionManager()
        messageService = MessageService(messageRepository, outbox, txManager, cache)
    }

    @AfterEach fun teardown() = cleanup()

    private fun buildTestApp() = buildApp()

    /** Creates a server message and returns its syncId. */
    private fun createAndSoftDelete(author: String, content: String): String {
        messageService.createServerMessage(author, content)
        val msg = messageRepository.listMessages(limit = 1, includeDeleted = false).first { it.author == author }
        messageRepository.softDelete(msg.syncId)
        return msg.syncId
    }

    @Test
    fun `restore redirects to trash page`() {
        val syncId = createAndSoftDelete("Restore Author", "Restore Content")
        val app = buildTestApp()

        val response = app(Request(POST, "/messages/restore/$syncId"))

        assertEquals(Status.FOUND, response.status)
        assertTrue(response.header("location")?.contains("/messages/trash") == true)
    }

    @Test
    fun `restored message no longer appears in trash`() {
        val syncId = createAndSoftDelete("Ghost Author", "Ghost Content")
        val app = buildTestApp()

        // Verify it's in trash before restore
        val trashBefore = app(Request(GET, "/messages/trash"))
        assertTrue(trashBefore.bodyString().contains("Ghost Author"), "Should be in trash before restore")

        app(Request(POST, "/messages/restore/$syncId"))

        val trashAfter = app(Request(GET, "/messages/trash"))
        assertFalse(trashAfter.bodyString().contains("Ghost Author"), "Should be gone from trash after restore")
    }

    @Test
    fun `restored message reappears on home page`() {
        val syncId = createAndSoftDelete("Risen Author", "Risen Content")
        val app = buildTestApp()

        // Confirm it's absent from home before restore
        val homeBefore = app(Request(GET, "/"))
        assertFalse(homeBefore.bodyString().contains("Risen Author"), "Should not be on home page while deleted")

        app(Request(POST, "/messages/restore/$syncId"))

        val homeAfter = app(Request(GET, "/"))
        assertTrue(homeAfter.bodyString().contains("Risen Author"), "Should reappear on home page after restore")
    }

    @Test
    fun `restoring unknown syncId is graceful`() {
        val app = buildTestApp()
        val response = app(Request(POST, "/messages/restore/non-existent-sync-id"))
        // Should redirect, not throw a 500
        assertEquals(Status.FOUND, response.status)
    }

    @Test
    fun `can create message then delete and restore via forms`() {
        val app = buildTestApp()

        // Create via form
        app(Request(POST, "/messages").form("author", "FormUser").form("content", "FormContent"))

        // Get syncId from repo
        val msg = messageRepository.listMessages(limit = 1, includeDeleted = false).first()
        val syncId = msg.syncId

        // Soft-delete it directly
        messageRepository.softDelete(syncId)
        assertFalse(messageRepository.listMessages(limit = 10, includeDeleted = false).any { it.syncId == syncId })

        // Restore via HTTP
        val restoreResponse = app(Request(POST, "/messages/restore/$syncId"))
        assertEquals(Status.FOUND, restoreResponse.status)

        // Confirm restored
        assertTrue(messageRepository.listMessages(limit = 10, includeDeleted = false).any { it.syncId == syncId })
    }
}
