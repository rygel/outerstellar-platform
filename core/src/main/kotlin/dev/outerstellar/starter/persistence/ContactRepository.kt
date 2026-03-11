package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.StoredContact
import dev.outerstellar.starter.sync.SyncContact

interface ContactRepository {
    fun listContacts(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        includeDeleted: Boolean = false
    ): List<ContactSummary>

    fun countContacts(query: String? = null, includeDeleted: Boolean = false): Long

    fun listDirtyContacts(): List<StoredContact>

    fun findBySyncId(syncId: String): StoredContact?

    fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact>

    fun createServerContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        company: String,
        companyAddress: String,
        department: String
    ): StoredContact

    fun createLocalContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        company: String,
        companyAddress: String,
        department: String
    ): StoredContact

    fun upsertSyncedContact(contact: SyncContact, dirty: Boolean): StoredContact

    fun markClean(syncIds: Collection<String>)

    fun getLastSyncEpochMs(): Long

    fun setLastSyncEpochMs(value: Long)

    fun seedStarterContacts()

    fun softDelete(syncId: String)

    fun restore(syncId: String)

    fun updateContact(contact: StoredContact): StoredContact

    fun markConflict(syncId: String, serverVersion: SyncContact)

    fun resolveConflict(syncId: String, resolvedContact: StoredContact)
}
