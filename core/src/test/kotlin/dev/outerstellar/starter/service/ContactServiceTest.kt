package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.StoredContact
import dev.outerstellar.starter.persistence.ContactRepository
import dev.outerstellar.starter.sync.SyncContact
import dev.outerstellar.starter.sync.SyncPushContactRequest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContactServiceTest {

    private val repository = mockk<ContactRepository>(relaxed = true)
    private val eventPublisher = mockk<EventPublisher>(relaxed = true)
    private val service = ContactService(repository, eventPublisher)

    private fun storedContact(
        syncId: String = "sync-1",
        name: String = "Alice",
        updatedAtEpochMs: Long = 100L,
        dirty: Boolean = false,
    ) =
        StoredContact(
            syncId = syncId,
            name = name,
            emails = listOf("alice@example.com"),
            phones = listOf("555-1234"),
            socialMedia = emptyList(),
            company = "ACME",
            companyAddress = "1 Main St",
            department = "Engineering",
            updatedAtEpochMs = updatedAtEpochMs,
            dirty = dirty,
            deleted = false,
        )

    private fun contactSummary(syncId: String = "sync-1") =
        ContactSummary(
            syncId = syncId,
            name = "Alice",
            emails = listOf("alice@example.com"),
            phones = listOf("555-1234"),
            socialMedia = emptyList(),
            company = "ACME",
            companyAddress = "1 Main St",
            department = "Engineering",
            updatedAtEpochMs = 100L,
            dirty = false,
        )

    @Test
    fun `listContacts delegates to repository`() {
        val expected = listOf(contactSummary())
        every { repository.listContacts(any(), any(), any()) } returns expected

        val result = service.listContacts()

        assertEquals(expected, result)
    }

    @Test
    fun `listContacts passes query to repository`() {
        every { repository.listContacts("alice", 50, 10) } returns emptyList()

        service.listContacts(query = "alice", limit = 50, offset = 10)

        verify { repository.listContacts("alice", 50, 10) }
    }

    @Test
    fun `countContacts delegates to repository`() {
        every { repository.countContacts(any()) } returns 42L

        val result = service.countContacts()

        assertEquals(42L, result)
    }

    @Test
    fun `getContactBySyncId returns contact from repository`() {
        val contact = storedContact()
        every { repository.findBySyncId("sync-1") } returns contact

        val result = service.getContactBySyncId("sync-1")

        assertEquals(contact, result)
    }

    @Test
    fun `getContactBySyncId returns null when not found`() {
        every { repository.findBySyncId("missing") } returns null

        val result = service.getContactBySyncId("missing")

        assertNull(result)
    }

    @Test
    fun `createContact delegates to repository and publishes refresh`() {
        val contact = storedContact()
        every {
            repository.createLocalContact(any(), any(), any(), any(), any(), any(), any())
        } returns contact

        service.createContact(
            "Alice",
            listOf("alice@example.com"),
            listOf("555-1234"),
            emptyList(),
            "ACME",
            "1 Main St",
            "Engineering",
        )

        verify { repository.createLocalContact(any(), any(), any(), any(), any(), any(), any()) }
        verify { eventPublisher.publishRefresh("contact-list-panel") }
    }

    @Test
    fun `createContact returns the created contact`() {
        val contact = storedContact()
        every {
            repository.createLocalContact(any(), any(), any(), any(), any(), any(), any())
        } returns contact

        val result =
            service.createContact(
                "Alice",
                listOf("alice@example.com"),
                listOf("555-1234"),
                emptyList(),
                "ACME",
                "1 Main St",
                "Engineering",
            )

        assertEquals(contact, result)
    }

    @Test
    fun `updateContact sets dirty flag before saving`() {
        val contact = storedContact(dirty = false)
        val dirtyContact = contact.copy(dirty = true)
        every { repository.updateContact(dirtyContact) } returns dirtyContact

        service.updateContact(contact)

        verify { repository.updateContact(dirtyContact) }
    }

    @Test
    fun `updateContact publishes refresh`() {
        val contact = storedContact()
        every { repository.updateContact(any()) } returns contact

        service.updateContact(contact)

        verify { eventPublisher.publishRefresh("contact-list-panel") }
    }

    @Test
    fun `deleteContact calls softDelete and publishes refresh`() {
        service.deleteContact("sync-1")

        verify { repository.softDelete("sync-1") }
        verify { eventPublisher.publishRefresh("contact-list-panel") }
    }

    @Test
    fun `getChangesSince returns contacts mapped to sync format`() {
        val contact = storedContact(syncId = "sync-1", updatedAtEpochMs = 500L)
        every { repository.findChangesSince(300L) } returns listOf(contact)

        val response = service.getChangesSince(300L)

        assertEquals(1, response.contacts.size)
        assertEquals("sync-1", response.contacts.first().syncId)
    }

    @Test
    fun `processPushRequest applies non-conflicting contacts`() {
        val pushed =
            SyncContact(
                syncId = "sync-new",
                name = "Bob",
                emails = emptyList(),
                phones = emptyList(),
                socialMedia = emptyList(),
                company = "",
                companyAddress = "",
                department = "",
                updatedAtEpochMs = 100L,
            )
        every { repository.findBySyncId("sync-new") } returns null

        val response = service.processPushRequest(SyncPushContactRequest(listOf(pushed)))

        assertEquals(1, response.appliedCount)
        assertTrue(response.conflicts.isEmpty())
        verify { repository.upsertSyncedContact(pushed, false) }
    }

    @Test
    fun `processPushRequest detects conflict when server is newer`() {
        val serverContact = storedContact(syncId = "sync-1", updatedAtEpochMs = 200L)
        val pushed =
            SyncContact(
                syncId = "sync-1",
                name = "Alice Modified",
                emails = emptyList(),
                phones = emptyList(),
                socialMedia = emptyList(),
                company = "",
                companyAddress = "",
                department = "",
                updatedAtEpochMs = 100L,
            )
        every { repository.findBySyncId("sync-1") } returns serverContact

        val response = service.processPushRequest(SyncPushContactRequest(listOf(pushed)))

        assertEquals(0, response.appliedCount)
        assertEquals(1, response.conflicts.size)
        assertEquals("sync-1", response.conflicts.first().syncId)
    }

    @Test
    fun `processPushRequest publishes refresh when contacts applied`() {
        val pushed =
            SyncContact(
                syncId = "sync-new",
                name = "Bob",
                emails = emptyList(),
                phones = emptyList(),
                socialMedia = emptyList(),
                company = "",
                companyAddress = "",
                department = "",
                updatedAtEpochMs = 100L,
            )
        every { repository.findBySyncId("sync-new") } returns null

        service.processPushRequest(SyncPushContactRequest(listOf(pushed)))

        verify { eventPublisher.publishRefresh("contact-list-panel") }
    }

    @Test
    fun `processPushRequest does not publish when nothing applied and no conflicts`() {
        val response = service.processPushRequest(SyncPushContactRequest(emptyList()))

        assertEquals(0, response.appliedCount)
        assertTrue(response.conflicts.isEmpty())
        verify(exactly = 0) { eventPublisher.publishRefresh(any()) }
    }
}
