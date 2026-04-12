package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.persistence.MessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.github.rygel.outerstellar.platform.sync.SyncPushRequest
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
        // getOrPut has a default impl in the interface but MockK relaxed mocks don't invoke it;
        // replicate the default behaviour so get/put interactions are still verifiable.
        every { cache.getOrPut(any(), any()) } answers
            {
                cache.get(firstArg()) ?: secondArg<() -> Any>()().also { cache.put(firstArg(), it) }
            }
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
        verify { cache.invalidateByPrefix("list:") }
        verify { cache.put("entity:${msg.syncId}", msg) }

        val localMsg = service.createLocalMessage("author", "content")
        verify(exactly = 2) { cache.invalidateByPrefix("list:") }
        verify { cache.put("entity:${localMsg.syncId}", localMsg) }

        service.processPushRequest(SyncPushRequest(emptyList()))
        // SyncPushRequest with empty list appliedCount is 0, no invalidation
        verify(exactly = 2) { cache.invalidateByPrefix("list:") }
    }

    @Test
    fun `findBySyncId uses entity cache`() {
        val syncIdValue = "id-1"
        val msg = mockk<io.github.rygel.outerstellar.platform.model.StoredMessage>(relaxed = true)
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
