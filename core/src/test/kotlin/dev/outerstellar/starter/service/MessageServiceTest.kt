package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPushRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageServiceTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val service = MessageService(repository)

    @Test
    fun `createServerMessage validates content`() {
        val error = kotlin.runCatching {
            service.createServerMessage("author", " ")
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertEquals("Content cannot be blank", error.message)
    }

    @Test
    fun `processPushRequest applies newer messages`() {
        val syncId = "123"
        val incoming = SyncMessage(syncId, "author", "new content", 200L, false)
        val current = StoredMessage(syncId, "author", "old content", 100L, false, false)

        every { repository.findBySyncId(syncId) } returns current

        val response = service.processPushRequest(SyncPushRequest(listOf(incoming)))

        assertEquals(1, response.appliedCount)
        assertEquals(0, response.conflicts.size)
        verify { repository.upsertSyncedMessage(incoming, dirty = false) }
    }

    @Test
    fun `processPushRequest detects conflicts for older incoming messages`() {
        val syncId = "123"
        val incoming = SyncMessage(syncId, "author", "old content", 100L, false)
        val current = StoredMessage(syncId, "author", "new content", 200L, false, false)

        every { repository.findBySyncId(syncId) } returns current

        val response = service.processPushRequest(SyncPushRequest(listOf(incoming)))

        assertEquals(0, response.appliedCount)
        assertEquals(1, response.conflicts.size)
        assertEquals(syncId, response.conflicts[0].syncId)
        verify(exactly = 0) { repository.upsertSyncedMessage(any(), any()) }
    }
}
