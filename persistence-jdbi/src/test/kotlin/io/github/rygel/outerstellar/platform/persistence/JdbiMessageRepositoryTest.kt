package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiMessageRepositoryTest : H2JdbiTest() {

    private val repo by lazy { JdbiMessageRepository(jdbi) }

    @Test
    fun `createLocalMessage and findBySyncId round-trips`() {
        val m = repo.createLocalMessage("Alice", "Hello world")
        val found = repo.findBySyncId(m.syncId)!!
        assertEquals("Alice", found.author)
        assertEquals("Hello world", found.content)
        assertFalse(found.deleted)
        assertTrue(found.dirty)
    }

    @Test
    fun `findBySyncId returns null for unknown id`() {
        assertNull(repo.findBySyncId("nonexistent"))
    }

    @Test
    fun `listMessages returns stored messages`() {
        repo.createLocalMessage("Alice", "First")
        repo.createLocalMessage("Bob", "Second")
        val results = repo.listMessages(query = null, year = null, limit = 10, offset = 0)
        assertEquals(2, results.size)
    }

    @Test
    fun `listMessages respects limit and offset`() {
        repeat(5) { repo.createLocalMessage("User", "Msg $it") }
        assertEquals(3, repo.listMessages(null, null, 3, 0).size)
        assertEquals(2, repo.listMessages(null, null, 10, 3).size)
    }

    @Test
    fun `listMessages excludes soft-deleted by default`() {
        val m = repo.createLocalMessage("Alice", "Delete me")
        repo.softDelete(m.syncId)
        val results = repo.listMessages(null, null, 10, 0, includeDeleted = false)
        assertTrue(results.none { it.syncId == m.syncId })
    }

    @Test
    fun `listMessages includes deleted when requested`() {
        val m = repo.createLocalMessage("Alice", "Deleted")
        repo.softDelete(m.syncId)
        val results = repo.listMessages(null, null, 10, 0, includeDeleted = true)
        assertTrue(results.any { it.syncId == m.syncId })
    }

    @Test
    fun `countMessages matches active message count`() {
        repo.createLocalMessage("Alice", "First")
        repo.createLocalMessage("Bob", "Second")
        assertEquals(2, repo.countMessages(null, null, false))
    }

    @Test
    fun `softDelete excludes message from active list`() {
        val m = repo.createLocalMessage("Alice", "To delete")
        repo.softDelete(m.syncId)
        assertTrue(
            repo.listMessages(null, null, 10, 0, includeDeleted = false).none {
                it.syncId == m.syncId
            }
        )
        assertTrue(
            repo.listMessages(null, null, 10, 0, includeDeleted = true).any {
                it.syncId == m.syncId
            }
        )
    }

    @Test
    fun `restore undeletes a soft-deleted message`() {
        val m = repo.createLocalMessage("Alice", "Restore me")
        repo.softDelete(m.syncId)
        assertTrue(
            repo.listMessages(null, null, 10, 0, includeDeleted = false).none {
                it.syncId == m.syncId
            }
        )
        repo.restore(m.syncId)
        assertTrue(
            repo.listMessages(null, null, 10, 0, includeDeleted = false).any {
                it.syncId == m.syncId
            }
        )
    }

    @Test
    fun `findChangesSince returns messages newer than epoch`() {
        val m1 = repo.createLocalMessage("Alice", "Old")
        val epoch = m1.updatedAtEpochMs
        Thread.sleep(5)
        val m2 = repo.createLocalMessage("Bob", "New")
        val changes = repo.findChangesSince(epoch)
        assertTrue(changes.none { it.syncId == m1.syncId })
        assertTrue(changes.any { it.syncId == m2.syncId })
    }

    @Test
    fun `createServerMessage is not dirty`() {
        val m = repo.createServerMessage("Server", "From server")
        assertNotNull(repo.findBySyncId(m.syncId))
        assertFalse(m.dirty)
    }

    @Test
    fun `markClean clears dirty flag`() {
        val m = repo.createLocalMessage("Alice", "Dirty message")
        assertTrue(repo.findBySyncId(m.syncId)!!.dirty)
        repo.markClean(listOf(m.syncId))
        assertFalse(repo.findBySyncId(m.syncId)!!.dirty)
    }

    @Test
    fun `listDirtyMessages returns only local messages`() {
        val local = repo.createLocalMessage("Alice", "Local")
        val server = repo.createServerMessage("Server", "Server msg")
        val dirty = repo.listDirtyMessages()
        assertTrue(dirty.any { it.syncId == local.syncId })
        assertTrue(dirty.none { it.syncId == server.syncId })
    }
}
