package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.MessageSummary
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
        val results = listOf(summary)
        
        every { cache.get(any()) } returns null
        every { repository.listMessages(any(), any(), any()) } returns results
        
        // First call - cache miss
        val firstResult = service.listMessages("test")
        assertEquals(results, firstResult)
        verify { repository.listMessages("test", 100, 0) }
        verify { cache.put("list:test:100:0", results) }
        
        // Second call - cache hit
        every { cache.get("list:test:100:0") } returns results
        val secondResult = service.listMessages("test")
        assertEquals(results, secondResult)
        // Verify repository was not called again
        verify(exactly = 1) { repository.listMessages(any(), any(), any()) }
    }

    @Test
    fun `write operations invalidate cache`() {
        service.createServerMessage("author", "content")
        verify { cache.invalidateAll() }

        service.createLocalMessage("author", "content")
        verify(exactly = 2) { cache.invalidateAll() }

        service.processPushRequest(SyncPushRequest(emptyList()))
        // SyncPushRequest with empty list appliedCount is 0, no invalidation
        verify(exactly = 2) { cache.invalidateAll() }
    }
}
