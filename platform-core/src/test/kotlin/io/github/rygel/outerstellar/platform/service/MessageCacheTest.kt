package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.StoredMessage
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class MessageCacheTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val cache = mockk<MessageCache>(relaxed = true)
    private val service = MessageService(repository, cache = cache)

    @Test
    fun `listMessages uses cache`() {
        val summary = MessageSummary("id", "author", "content", 1000L, false)
        val items = listOf(summary)
        val results = PagedResult(items, PaginationMetadata(1, 100, 1L))

        every { cache.getMessageListOrPut(any(), any()) } answers
            {
                val loader = secondArg<() -> PagedResult<MessageSummary>>()
                loader().also { cache.putMessageList(firstArg(), it) }
            }
        every { repository.listMessagesWithTotal(any(), any(), any(), any(), any()) } returns
            io.github.rygel.outerstellar.platform.model.PagedQueryResult(items, 1L)

        val firstResult = service.listMessages("test")
        assertEquals(results, firstResult)
        verify { repository.listMessagesWithTotal("test", null, 100, 0, false) }
        verify { cache.putMessageList("list:test:null:100:0", results) }

        every { cache.getMessageListOrPut(any(), any()) } returns results
        val secondResult = service.listMessages("test")
        assertEquals(results, secondResult)
        verify(exactly = 1) { repository.listMessagesWithTotal(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `write operations invalidate cache and use entity cache`() {
        val msg = service.createServerMessage("author", "content")
        verify { cache.invalidateNamespace("list") }
        verify { cache.putMessage(msg.syncId, msg) }

        val localMsg = service.createLocalMessage("author", "content")
        verify(exactly = 2) { cache.invalidateNamespace("list") }
        verify { cache.putMessage(localMsg.syncId, localMsg) }

        service.processPushRequest(SyncPushRequest(emptyList()))
        verify(exactly = 2) { cache.invalidateNamespace("list") }
    }

    @Test
    fun `findBySyncId uses entity cache`() {
        val syncIdValue = "id-1"
        val msg = mockk<StoredMessage>(relaxed = true)
        every { msg.syncId } returns syncIdValue

        every { cache.getMessage(syncIdValue) } returns null
        every { repository.findBySyncId(syncIdValue) } returns msg

        val firstResult = service.findBySyncId(syncIdValue)
        assertEquals(msg, firstResult)
        verify { repository.findBySyncId(syncIdValue) }
        verify { cache.putMessage(syncIdValue, msg) }

        every { cache.getMessage(syncIdValue) } returns msg
        val secondResult = service.findBySyncId(syncIdValue)
        assertEquals(msg, secondResult)
        verify(exactly = 1) { repository.findBySyncId(syncIdValue) }
    }
}
