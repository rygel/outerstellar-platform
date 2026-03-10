package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PagedResult
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.persistence.MessageCache
import dev.outerstellar.starter.persistence.MessageRepository
import dev.outerstellar.starter.sync.SyncPushRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class MessageCacheTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val cache = mockk<MessageCache>(relaxed = true)
    private val service = MessageService(repository, cache = cache)

    @Test
    fun `listMessages uses cache`() {
        val summary = MessageSummary("id", "author", "content", 1000L, false)
        val items = listOf(summary)
        val results = PagedResult(items, PaginationMetadata(1, 100, 1L))
        
        every { cache.get(any()) } returns null
        every { repository.listMessages(any(), any(), any(), any()) } returns items
        every { repository.countMessages(any(), any()) } returns 1L
        
        // First call - cache miss
        val firstResult = service.listMessages("test")
        assertEquals(results, firstResult)
        verify { repository.listMessages("test", null, 100, 0) }
        verify { cache.put("list:test:null:100:0", results) }
        
        // Second call - cache hit
        every { cache.get("list:test:null:100:0") } returns results
        val secondResult = service.listMessages("test")
        assertEquals(results, secondResult)
        // Verify repository was not called again
        verify(exactly = 1) { repository.listMessages(any(), any(), any(), any()) }
    }

    @Test
    fun `write operations invalidate cache and use entity cache`() {
        val msg = service.createServerMessage("author", "content")
        verify { cache.invalidateAll() }
        verify { cache.put("entity:${msg.syncId}", msg) }

        val localMsg = service.createLocalMessage("author", "content")
        verify(exactly = 2) { cache.invalidateAll() }
        verify { cache.put("entity:${localMsg.syncId}", localMsg) }

        service.processPushRequest(SyncPushRequest(emptyList()))
        // SyncPushRequest with empty list appliedCount is 0, no invalidation
        verify(exactly = 2) { cache.invalidateAll() }
    }

    @Test
    fun `findBySyncId uses entity cache`() {
        val syncIdValue = "id-1"
        val msg = mockk<dev.outerstellar.starter.model.StoredMessage>(relaxed = true)
        every { msg.syncId } returns syncIdValue
        
        every { cache.get("entity:$syncIdValue") } returns null
        every { repository.findBySyncId(syncIdValue) } returns msg
        
        // Cache miss
        val firstResult = service.findBySyncId(syncIdValue)
        assertEquals(msg, firstResult)
        verify { repository.findBySyncId(syncIdValue) }
        verify { cache.put("entity:$syncIdValue", msg) }
        
        // Cache hit
        every { cache.get("entity:$syncIdValue") } returns msg
        val secondResult = service.findBySyncId(syncIdValue)
        assertEquals(msg, secondResult)
        verify(exactly = 1) { repository.findBySyncId(syncIdValue) }
    }
}
