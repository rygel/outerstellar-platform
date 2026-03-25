package io.github.rygel.stellar.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CaffeineStellarCacheTest {

    private val cache: StellarCache = CaffeineStellarCache()

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get<String>("nonexistent"))
    }

    @Test
    fun `put and get round-trips value`() {
        cache.put("key1", "value1")
        assertEquals("value1", cache.get<String>("key1"))
    }

    @Test
    fun `put overwrites previous value`() {
        cache.put("key1", "old")
        cache.put("key1", "new")
        assertEquals("new", cache.get<String>("key1"))
    }

    @Test
    fun `invalidate removes single key`() {
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        cache.invalidate("key1")

        assertNull(cache.get<String>("key1"))
        assertEquals("value2", cache.get<String>("key2"))
    }

    @Test
    fun `invalidateByPrefix removes matching keys`() {
        cache.put("communities:popular:25:0", listOf("a", "b"))
        cache.put("communities:popular:10:0", listOf("c"))
        cache.put("feed:popular:HOT:ALL:25:0", listOf("d"))

        cache.invalidateByPrefix("communities:popular:")

        assertNull(cache.get<List<String>>("communities:popular:25:0"))
        assertNull(cache.get<List<String>>("communities:popular:10:0"))
        assertEquals(listOf("d"), cache.get<List<String>>("feed:popular:HOT:ALL:25:0"))
    }

    @Test
    fun `invalidateByPrefix with no matches does not throw`() {
        cache.put("key1", "value1")
        cache.invalidateByPrefix("nonexistent:")
        assertEquals("value1", cache.get<String>("key1"))
    }

    @Test
    fun `get returns correct type`() {
        cache.put("intKey", 42)
        cache.put("listKey", listOf(1, 2, 3))

        assertEquals(42, cache.get<Int>("intKey"))
        assertEquals(listOf(1, 2, 3), cache.get<List<Int>>("listKey"))
    }
}
