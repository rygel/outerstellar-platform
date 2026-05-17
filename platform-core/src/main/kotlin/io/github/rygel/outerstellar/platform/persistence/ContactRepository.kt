package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.PagedQueryResult
import io.github.rygel.outerstellar.platform.model.StoredContact
import io.github.rygel.outerstellar.platform.sync.SyncContact

@Suppress("TooManyFunctions")
interface ContactRepository {
    fun listContacts(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        includeDeleted: Boolean = false,
    ): List<ContactSummary>

    fun countContacts(query: String? = null, includeDeleted: Boolean = false): Long

    fun listContactsWithTotal(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0,
        includeDeleted: Boolean = false,
    ): PagedQueryResult<ContactSummary>

    fun listDirtyContacts(limit: Int = 500): List<StoredContact>

    fun findBySyncId(syncId: String): StoredContact?

    fun findChangesSince(updatedAtEpochMs: Long, limit: Int = 500): List<StoredContact>

    @Suppress("LongParameterList")
    fun createServerContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact

    @Suppress("LongParameterList")
    fun createLocalContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact

    fun upsertSyncedContact(contact: SyncContact, dirty: Boolean): StoredContact

    fun markClean(syncIds: Collection<String>)

    fun getLastSyncEpochMs(): Long

    fun setLastSyncEpochMs(value: Long)

    fun seedContacts()

    fun softDelete(syncId: String)

    fun restore(syncId: String)

    fun updateContact(contact: StoredContact): StoredContact

    fun markConflict(syncId: String, serverVersion: SyncContact)

    fun resolveConflict(syncId: String, resolvedContact: StoredContact)
}
