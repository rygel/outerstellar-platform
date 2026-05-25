package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PagedResult
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.model.StoredMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineMessageCacheTest {

    private val cache = CaffeineMessageCache()

    @Test
    fun `invalidateNamespace bumps generation making old keys stale`() {
        val result = PagedResult(listOf(MessageSummary("id", "a", "c", 0L, false)), PaginationMetadata(1, 10, 1L))
        val msg = StoredMessage("abc-123", "author", "content", 0L, false, false, 1L)

        cache.putMessageList("list:0:all:null:10:0", result)
        cache.putMessage("abc-123", msg)

        cache.invalidateNamespace("list")

        assertNull(cache.getMessageList("list:0:all:null:10:0"))
        assertEquals(msg, cache.getMessage("abc-123"))
    }

    @Test
    fun `invalidateNamespace with no prior keys is a no-op`() {
        val msg = StoredMessage("abc-123", "author", "content", 0L, false, false, 1L)
        cache.putMessage("abc-123", msg)

        cache.invalidateNamespace("list")

        assertEquals(msg, cache.getMessage("abc-123"))
    }

    @Test
    fun `generationKey includes current generation`() {
        assertEquals("list:0:query", cache.generationKey("list", "query"))
        cache.invalidateNamespace("list")
        assertEquals("list:1:query", cache.generationKey("list", "query"))
    }

    @Test
    fun `constructor accepts custom size and TTL`() {
        val custom = CaffeineMessageCache(maxSize = 50, ttlMinutes = 1)
        val msg = StoredMessage("key", "author", "content", 0L, false, false, 1L)
        custom.putMessage("key", msg)
        assertEquals(msg, custom.getMessage("key"))
    }
}
