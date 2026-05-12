package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.StoredMessage
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.persistence.CaffeineMessageCache
import io.github.rygel.outerstellar.platform.persistence.MessageRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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

    @Test
    fun `createServerMessage rejects author exceeding max length`() {
        val longAuthor = "a".repeat(MessageService.MAX_AUTHOR_LENGTH + 1)
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(ValidationException::class.java) {
                service.createServerMessage(longAuthor, "content")
            }
        assertTrue(ex.errors.any { it.contains("Author") && it.contains("${MessageService.MAX_AUTHOR_LENGTH}") })
    }

    @Test
    fun `createServerMessage rejects content exceeding max length`() {
        val longContent = "c".repeat(MessageService.MAX_CONTENT_LENGTH + 1)
        val ex =
            org.junit.jupiter.api.Assertions.assertThrows(ValidationException::class.java) {
                service.createServerMessage("Alice", longContent)
            }
        assertTrue(ex.errors.any { it.contains("Content") && it.contains("${MessageService.MAX_CONTENT_LENGTH}") })
    }

    @Test
    fun `createLocalMessage rejects author exceeding max length`() {
        val longAuthor = "a".repeat(MessageService.MAX_AUTHOR_LENGTH + 1)
        org.junit.jupiter.api.Assertions.assertThrows(ValidationException::class.java) {
            service.createLocalMessage(longAuthor, "content")
        }
    }

    @Test
    fun `createLocalMessage rejects content exceeding max length`() {
        val longContent = "c".repeat(MessageService.MAX_CONTENT_LENGTH + 1)
        org.junit.jupiter.api.Assertions.assertThrows(ValidationException::class.java) {
            service.createLocalMessage("Alice", longContent)
        }
    }

    @Test
    fun `createServerMessage accepts author at max length`() {
        val author = "a".repeat(MessageService.MAX_AUTHOR_LENGTH)
        every { repository.createServerMessage(author, "content") } returns
            StoredMessage("id", author, "content", 0L, false, false, 1L)
        service.createServerMessage(author, "content")
    }

    @Test
    fun `createServerMessage accepts content at max length`() {
        val content = "c".repeat(MessageService.MAX_CONTENT_LENGTH)
        every { repository.createServerMessage("Alice", content) } returns
            StoredMessage("id", "Alice", content, 0L, false, false, 1L)
        service.createServerMessage("Alice", content)
    }
}
