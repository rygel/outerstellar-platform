package io.github.rygel.outerstellar.platform.service

<<<<<<< HEAD
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
=======
>>>>>>> origin/main
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.StoredMessage
<<<<<<< HEAD
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
=======
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
>>>>>>> origin/main

class MessageServiceTest {
    private val repository = mockk<MessageRepository>(relaxed = true)
    private val service = MessageService(repository)

    @Test
    fun `listMessages returns items from repository`() {
        val summary = MessageSummary("id-1", "author", "content", 1000L, false)
        val items = listOf(summary)
        val paged = PagedResult(items, PaginationMetadata(1, 10, 1L))

        every { repository.listMessages("test", null, 10, 0) } returns items
        every { repository.countMessages("test", null) } returns 1L

        val result = service.listMessages("test", limit = 10, offset = 0)
        assertEquals(paged, result)
    }

    @Test
    fun `listDirtyMessages returns dirty items from repository`() {
        val msg = StoredMessage("id-1", "author", "content", 1000L, true, false, 1L)
        val items = listOf(msg)
        every { repository.listDirtyMessages() } returns items

        val result = service.listDirtyMessages()
        assertEquals(items, result)
    }
<<<<<<< HEAD

    @Test
    fun `createServerMessage preserves entity cache entry after mutation`() {
        val syncId = UUID.randomUUID().toString()
        val msg = StoredMessage(syncId, "Alice", "hello", 1000L, false, false, 1L)
        every { repository.createServerMessage("Alice", "hello") } returns msg

        val cache = CaffeineMessageCache()
        val serviceWithCache = MessageService(repository, cache = cache)

        serviceWithCache.createServerMessage("Alice", "hello")

        // Entity key must still be present — not nuked by invalidateAll
        assertEquals(msg, cache.get("entity:$syncId"))
    }

    @Test
    fun `resolveConflict invalidates entity cache entry`() {
        val syncId = UUID.randomUUID().toString()
        val conflict = """{"syncId":"$syncId","author":"Bob","content":"conflict","updatedAtEpochMs":2000}"""
        val existing = StoredMessage(syncId, "Alice", "original", 1000L, false, false, 1L, syncConflict = conflict)
        every { repository.findBySyncId(syncId) } returns existing
        every { repository.resolveConflict(syncId, any()) } just Runs

        val cache = CaffeineMessageCache()
        cache.put("entity:$syncId", existing)
        val serviceWithCache = MessageService(repository, cache = cache)

        serviceWithCache.resolveConflict(syncId, ConflictStrategy.SERVER)

        assertNull(cache.get("entity:$syncId"))
    }

    @Test
    fun `createServerMessage clears list cache entries`() {
        val syncId = UUID.randomUUID().toString()
        val msg = StoredMessage(syncId, "Alice", "hello", 1000L, false, false, 1L)
        every { repository.createServerMessage("Alice", "hello") } returns msg

        val cache = CaffeineMessageCache()
        cache.put("list:null:null:10:0", "stale-result")

        val serviceWithCache = MessageService(repository, cache = cache)
        serviceWithCache.createServerMessage("Alice", "hello")

        assertNull(cache.get("list:null:null:10:0"))
    }
=======
>>>>>>> origin/main
}
