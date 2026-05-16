package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineMessageCacheTest {

    private val cache = CaffeineMessageCache()

    @Test
    fun `invalidateNamespace bumps generation making old keys stale`() {
        cache.put("list:0:all:null:10:0", "result-1")
        cache.put("entity:abc-123", "message-1")

        cache.invalidateNamespace("list")

        assertNull(cache.get("list:0:all:null:10:0"))
        assertEquals("message-1", cache.get("entity:abc-123"))
    }

    @Test
    fun `invalidateNamespace with no prior keys is a no-op`() {
        cache.put("entity:abc-123", "message-1")

        cache.invalidateNamespace("list")

        assertEquals("message-1", cache.get("entity:abc-123"))
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
        custom.put("key", "value")
        assertEquals("value", custom.get("key"))
    }
}
