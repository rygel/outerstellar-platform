package io.github.rygel.outerstellar.platform.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JdbiContactRepositoryTest : H2JdbiTest() {

    private val repo by lazy { JdbiContactRepository(jdbi) }

    private fun createLocal(
        name: String = "Alice Smith",
        emails: List<String> = listOf("alice@example.com"),
        phones: List<String> = listOf("+1 555-0101"),
        socialMedia: List<String> = listOf("@alice"),
        company: String = "Acme",
        companyAddress: String = "1 Main St",
        department: String = "Engineering",
    ) =
        repo.createLocalContact(
            name,
            emails,
            phones,
            socialMedia,
            company,
            companyAddress,
            department,
        )

    @Test
    fun `createLocalContact and findBySyncId round-trips`() {
        val c =
            createLocal(
                "Bob Jones",
                listOf("bob@example.com"),
                listOf("+1 555-0202"),
                listOf("@bob"),
                "Globex",
                "2 Elm St",
                "Sales",
            )
        val found = repo.findBySyncId(c.syncId)!!
        assertEquals("Bob Jones", found.name)
        assertEquals(listOf("bob@example.com"), found.emails)
        assertEquals(listOf("+1 555-0202"), found.phones)
        assertEquals(listOf("@bob"), found.socialMedia)
        assertEquals("Globex", found.company)
        assertTrue(found.dirty)
        assertFalse(found.deleted)
    }

    @Test
    fun `findBySyncId returns null for unknown id`() {
        assertNull(repo.findBySyncId("nonexistent"))
    }

    @Test
    fun `createServerContact is not dirty`() {
        val c =
            repo.createServerContact(
                "Server Contact",
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                "",
                "",
            )
        assertFalse(repo.findBySyncId(c.syncId)!!.dirty)
    }

    @Test
    fun `listContacts returns active contacts`() {
        createLocal("Alice")
        createLocal("Bob")
        val results = repo.listContacts(null, 10, 0)
        assertEquals(2, results.size)
    }

    @Test
    fun `listContacts is ordered by name`() {
        createLocal("Zara")
        createLocal("Alice")
        createLocal("Mike")
        val results = repo.listContacts(null, 10, 0)
        assertEquals("Alice", results[0].name)
        assertEquals("Mike", results[1].name)
        assertEquals("Zara", results[2].name)
    }

    @Test
    fun `listContacts respects limit and offset`() {
        repeat(5) { createLocal("Contact $it") }
        assertEquals(3, repo.listContacts(null, 3, 0).size)
        assertEquals(2, repo.listContacts(null, 10, 3).size)
    }

    @Test
    fun `listContacts filters by query on name`() {
        createLocal("Alice Smith")
        createLocal("Bob Jones")
        val results = repo.listContacts("alice", 10, 0)
        assertEquals(1, results.size)
        assertEquals("Alice Smith", results[0].name)
    }

    @Test
    fun `listContacts excludes deleted by default`() {
        val c = createLocal("Delete Me")
        repo.softDelete(c.syncId)
        val results = repo.listContacts(null, 10, 0)
        assertTrue(results.none { it.syncId == c.syncId })
    }

    @Test
    fun `listContacts includes deleted when requested`() {
        val c = createLocal("Deleted")
        repo.softDelete(c.syncId)
        val results = repo.listContacts(null, 10, 0, includeDeleted = true)
        assertTrue(results.any { it.syncId == c.syncId })
    }

    @Test
    fun `countContacts matches active contacts`() {
        createLocal("Alice")
        createLocal("Bob")
        assertEquals(2, repo.countContacts(null, false))
    }

    @Test
    fun `softDelete marks contact as deleted`() {
        val c = createLocal()
        repo.softDelete(c.syncId)
        assertTrue(repo.findBySyncId(c.syncId)!!.deleted)
    }

    @Test
    fun `restore undeletes a soft-deleted contact`() {
        val c = createLocal()
        repo.softDelete(c.syncId)
        assertTrue(repo.findBySyncId(c.syncId)!!.deleted)
        repo.restore(c.syncId)
        assertFalse(repo.findBySyncId(c.syncId)!!.deleted)
    }

    @Test
    fun `markClean clears dirty flag`() {
        val c = createLocal()
        assertTrue(repo.findBySyncId(c.syncId)!!.dirty)
        repo.markClean(listOf(c.syncId))
        assertFalse(repo.findBySyncId(c.syncId)!!.dirty)
    }

    @Test
    fun `listDirtyContacts returns only local contacts`() {
        val local = createLocal("Local Contact")
        val server =
            repo.createServerContact(
                "Server Contact",
                emptyList(),
                emptyList(),
                emptyList(),
                "",
                "",
                "",
            )
        val dirty = repo.listDirtyContacts()
        assertTrue(dirty.any { it.syncId == local.syncId })
        assertTrue(dirty.none { it.syncId == server.syncId })
    }

    @Test
    fun `findChangesSince returns contacts newer than epoch`() {
        val c1 = createLocal("Old Contact")
        val epoch = c1.updatedAtEpochMs
        Thread.sleep(5)
        val c2 = createLocal("New Contact")
        val changes = repo.findChangesSince(epoch)
        assertTrue(changes.none { it.syncId == c1.syncId })
        assertTrue(changes.any { it.syncId == c2.syncId })
    }

    @Test
    fun `contact collections round-trip correctly`() {
        val c =
            repo.createLocalContact(
                "Multi Contact",
                listOf("a@example.com", "b@example.com"),
                listOf("+1 111", "+1 222"),
                listOf("@handle1", "@handle2"),
                "Corp",
                "Addr",
                "Dept",
            )
        val found = repo.findBySyncId(c.syncId)!!
        assertEquals(2, found.emails.size)
        assertEquals(2, found.phones.size)
        assertEquals(2, found.socialMedia.size)
        assertTrue(found.emails.contains("a@example.com"))
        assertTrue(found.emails.contains("b@example.com"))
    }

    @Test
    fun `getLastSyncEpochMs returns 0 initially`() {
        assertEquals(0L, repo.getLastSyncEpochMs())
    }

    @Test
    fun `setLastSyncEpochMs and getLastSyncEpochMs round-trips`() {
        repo.setLastSyncEpochMs(12345L)
        assertEquals(12345L, repo.getLastSyncEpochMs())
    }

    @Test
    fun `setLastSyncEpochMs is idempotent`() {
        repo.setLastSyncEpochMs(100L)
        repo.setLastSyncEpochMs(200L)
        assertEquals(200L, repo.getLastSyncEpochMs())
    }

    @Test
    fun `seedContacts inserts three contacts`() {
        repo.seedContacts()
        assertEquals(3, repo.countContacts(null, false))
    }

    @Test
    fun `seedContacts is idempotent`() {
        repo.seedContacts()
        repo.seedContacts()
        assertEquals(3, repo.countContacts(null, false))
    }
}
