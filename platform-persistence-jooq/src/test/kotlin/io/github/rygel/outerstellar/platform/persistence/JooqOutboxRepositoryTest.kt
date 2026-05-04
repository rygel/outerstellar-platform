package io.github.rygel.outerstellar.platform.persistence

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JooqOutboxRepositoryTest : JooqTest() {

    private val repo by lazy { JooqOutboxRepository(dsl) }

    private fun entry(payloadType: String = "TestEvent", payload: String = "{}") =
        OutboxEntry(id = UUID.randomUUID(), payloadType = payloadType, payload = payload, status = "PENDING")

    @Test
    fun `save and listPending round-trips`() {
        val e = entry("UserCreated", """{"id":"abc"}""")
        repo.save(e)
        val pending = repo.listPending(10)
        assertEquals(1, pending.size)
        assertEquals("UserCreated", pending[0].payloadType)
        assertEquals("""{"id":"abc"}""", pending[0].payload)
        assertEquals("PENDING", pending[0].status)
    }

    @Test
    fun `listPending respects limit`() {
        repeat(5) { repo.save(entry()) }
        assertEquals(3, repo.listPending(3).size)
    }

    @Test
    fun `listPending returns entries in insertion order`() {
        val ids =
            (1..3).map {
                val e = entry("Event$it")
                repo.save(e)
                e.id
            }
        val pending = repo.listPending(10)
        assertEquals(ids[0], pending[0].id)
        assertEquals(ids[2], pending[2].id)
    }

    @Test
    fun `markProcessed removes entry from pending`() {
        val e = entry()
        repo.save(e)
        assertEquals(1, repo.listPending(10).size)
        repo.markProcessed(e.id)
        assertTrue(repo.listPending(10).isEmpty())
    }

    @Test
    fun `markFailed moves entry to failed list`() {
        val e = entry()
        repo.save(e)
        repo.markFailed(e.id, "connection timeout")
        assertTrue(repo.listPending(10).isEmpty())
        val failed = repo.listFailed()
        assertEquals(1, failed.size)
        assertEquals("FAILED", failed[0].status)
    }

    @Test
    fun `getStats returns counts by status`() {
        repo.save(entry())
        repo.save(entry())
        val e3 = entry()
        repo.save(e3)
        repo.markProcessed(e3.id)
        val stats = repo.getStats()
        assertEquals(2, stats["PENDING"])
        assertEquals(1, stats["PROCESSED"])
    }

    @Test
    fun `getStats returns empty map when no entries`() {
        assertTrue(repo.getStats().isEmpty())
    }

    @Test
    fun `listFailed returns only failed entries`() {
        val e1 = entry()
        val e2 = entry()
        repo.save(e1)
        repo.save(e2)
        repo.markFailed(e1.id, "error")
        val failed = repo.listFailed()
        assertEquals(1, failed.size)
        assertEquals(e1.id, failed[0].id)
    }

    @Test
    fun `listPending excludes processed and failed entries`() {
        val e1 = entry()
        val e2 = entry()
        val e3 = entry()
        repo.save(e1)
        repo.save(e2)
        repo.save(e3)
        repo.markProcessed(e2.id)
        repo.markFailed(e3.id, "err")
        val pending = repo.listPending(10)
        assertEquals(1, pending.size)
        assertEquals(e1.id, pending[0].id)
    }
}
