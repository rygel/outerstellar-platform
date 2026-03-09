package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.StoredMessage
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import dev.outerstellar.starter.sync.SyncMessage
import dev.outerstellar.starter.sync.SyncPushRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageServiceTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val outboxRepository = mockk<OutboxRepository>(relaxed = true)
    private val transactionManager = object : TransactionManager {
        override fun <T> inTransaction(block: () -> T): T = block()
    }
    private val service = MessageService(repository, outboxRepository, transactionManager)

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

    @Test
    fun `createServerMessage saves to outbox within transaction`() {
        val author = "Junie"
        val content = "Hello outbox"
        val syncId = "sync-123"
        val message = StoredMessage(syncId, author, content, System.currentTimeMillis(), false, false)

        every { repository.createServerMessage(author, content) } returns message

        val result = service.createServerMessage(author, content)

        assertEquals(message, result)
        verify { repository.createServerMessage(author, content) }
        verify { outboxRepository.save(match { it.payload == syncId && it.payloadType == "MESSAGE_CREATED" }) }
    }

    @Test
    fun `updateMessage handles optimistic locking failure`() {
        val syncId = "123"
        val message = StoredMessage(syncId, "author", "content", 100L, false, false, version = 1)
        
        every { repository.updateMessage(message) } throws IllegalStateException("Optimistic locking failure for message $syncId")

        val error = kotlin.runCatching {
            service.updateMessage(message)
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Optimistic locking failure for message $syncId", error.message)
    }

    @Test
    fun `updateMessage increments version and records to outbox`() {
        val syncId = "123"
        val message = StoredMessage(syncId, "author", "content", 100L, false, false, version = 1)
        val updated = message.copy(version = 2)

        every { repository.updateMessage(message) } returns updated

        val result = service.updateMessage(message)

        assertEquals(2, result.version)
        verify { repository.updateMessage(message) }
        verify { outboxRepository.save(match { it.payload == syncId && it.payloadType == "MESSAGE_UPDATED" }) }
    }
}
