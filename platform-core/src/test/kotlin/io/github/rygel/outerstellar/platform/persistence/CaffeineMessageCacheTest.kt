package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineMessageCacheTest {

    private val cache = CaffeineMessageCache()

    @Test
    fun `invalidateByPrefix removes only matching keys`() {
        cache.put("list:all:null:10:0", "result-1")
        cache.put("list:q:null:10:0", "result-2")
        cache.put("entity:abc-123", "message-1")

        cache.invalidateByPrefix("list:")

        assertNull(cache.get("list:all:null:10:0"))
        assertNull(cache.get("list:q:null:10:0"))
        assertEquals("message-1", cache.get("entity:abc-123"))
    }

    @Test
    fun `invalidateByPrefix with no matching keys is a no-op`() {
        cache.put("entity:abc-123", "message-1")

        cache.invalidateByPrefix("list:")

        assertEquals("message-1", cache.get("entity:abc-123"))
    }
}
