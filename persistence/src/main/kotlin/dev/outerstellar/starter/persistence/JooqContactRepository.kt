package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.CONTACTS
import dev.outerstellar.starter.jooq.tables.references.CONTACT_EMAILS
import dev.outerstellar.starter.jooq.tables.references.CONTACT_PHONES
import dev.outerstellar.starter.jooq.tables.references.SYNC_STATE
import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.StoredContact
import dev.outerstellar.starter.sync.SyncContact
import org.http4k.format.Jackson
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import java.util.UUID

@Suppress("TooManyFunctions")
class JooqContactRepository(
    private val primaryDsl: DSLContext,
    private val replicaDsl: DSLContext = primaryDsl
) : ContactRepository {
    private val logger = LoggerFactory.getLogger(JooqContactRepository::class.java)

    private fun getFilterConditions(query: String?, includeDeleted: Boolean = false): Condition {
        var condition = DSL.noCondition()
            .let {
                val deletedField = CONTACTS.field("DELETED", Boolean::class.java)
                if (deletedField != null) it.and(deletedField.eq(false)) else it
            }

        if (!query.isNullOrBlank()) {
            condition = condition.and(
                CONTACTS.NAME.containsIgnoreCase(query)
                    .or(CONTACTS.COMPANY.containsIgnoreCase(query))
            )
        }

        return condition
    }

    override fun listContacts(
        query: String?,
        limit: Int,
        offset: Int,
        includeDeleted: Boolean
    ): List<ContactSummary> {
        val syncConflictField = CONTACTS.field("SYNC_CONFLICT", String::class.java)
        val results = replicaDsl
            .select(
                CONTACTS.ID,
                CONTACTS.SYNC_ID,
                CONTACTS.NAME,
                emailsField,
                phonesField,
                CONTACTS.COMPANY,
                CONTACTS.COMPANY_ADDRESS,
                CONTACTS.DEPARTMENT,
                CONTACTS.UPDATED_AT_EPOCH_MS,
                CONTACTS.DIRTY,
                CONTACTS.DELETED,
                CONTACTS.VERSION,
                syncConflictField
            )
            .from(CONTACTS)
            .where(getFilterConditions(query, includeDeleted))
            .orderBy(CONTACTS.NAME.asc())
            .limit(limit)
            .offset(offset)
            .fetch(::toStoredContact)

        return results.map(StoredContact::toSummary)
    }

    override fun countContacts(query: String?, includeDeleted: Boolean): Long {
        return replicaDsl.fetchCount(CONTACTS, getFilterConditions(query, includeDeleted)).toLong()
    }

    override fun listDirtyContacts(): List<StoredContact> =
        primaryDsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.DIRTY.eq(true))
            .fetch(::toStoredContact)

    override fun findBySyncId(syncId: String): StoredContact? =
        replicaDsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .fetchOne(::toStoredContact)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact> =
        replicaDsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs))
            .fetch(::toStoredContact)

    override fun createServerContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        company: String,
        companyAddress: String,
        department: String
    ): StoredContact = insertContact(name, emails, phones, company, companyAddress, department, false)

    override fun createLocalContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        company: String,
        companyAddress: String,
        department: String
    ): StoredContact = insertContact(name, emails, phones, company, companyAddress, department, true)

    override fun upsertSyncedContact(contact: SyncContact, dirty: Boolean): StoredContact {
        val existing = findBySyncId(contact.syncId)

        if (existing == null) {
            primaryDsl
                .insertInto(CONTACTS)
                .set(CONTACTS.SYNC_ID, contact.syncId)
                .set(CONTACTS.NAME, contact.name)
                .set(CONTACTS.COMPANY, contact.company)
                .set(CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                .set(CONTACTS.DEPARTMENT, contact.department)
                .set(CONTACTS.UPDATED_AT_EPOCH_MS, contact.updatedAtEpochMs)
                .set(CONTACTS.DIRTY, dirty)
                .set(CONTACTS.DELETED, contact.deleted)
                .set(CONTACTS.VERSION, 1L)
                .execute()
        } else {
            primaryDsl
                .update(CONTACTS)
                .set(CONTACTS.NAME, contact.name)
                .set(CONTACTS.COMPANY, contact.company)
                .set(CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                .set(CONTACTS.DEPARTMENT, contact.department)
                .set(CONTACTS.UPDATED_AT_EPOCH_MS, contact.updatedAtEpochMs)
                .set(CONTACTS.DIRTY, dirty)
                .set(CONTACTS.DELETED, contact.deleted)
                .set(CONTACTS.VERSION, existing.version + 1)
                .where(CONTACTS.SYNC_ID.eq(contact.syncId))
                .execute()
        }

        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        primaryDsl.update(CONTACTS).set(CONTACTS.DIRTY, false).where(CONTACTS.SYNC_ID.`in`(syncIds)).execute()
    }

    override fun getLastSyncEpochMs(): Long =
        replicaDsl
            .select(SYNC_STATE.STATE_VALUE)
            .from(SYNC_STATE)
            .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
            .fetchOne(SYNC_STATE.STATE_VALUE) ?: 0L

    override fun setLastSyncEpochMs(value: Long) {
        val hasState = primaryDsl.fetchCount(SYNC_STATE, SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
        if (hasState) {
            primaryDsl
                .update(SYNC_STATE)
                .set(SYNC_STATE.STATE_VALUE, value)
                .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
                .execute()
        } else {
            primaryDsl
                .insertInto(SYNC_STATE)
                .set(SYNC_STATE.STATE_KEY, lastSyncStateKey)
                .set(SYNC_STATE.STATE_VALUE, value)
                .execute()
        }
    }

    override fun seedStarterContacts() {
        if (replicaDsl.fetchCount(
                CONTACTS,
                DSL.noCondition().let {
                    val deletedField = CONTACTS.field("DELETED", Boolean::class.java)
                    if (deletedField != null) it.and(deletedField.eq(false)) else it
                }
            ) > 0
        ) return

        createServerContact(
            "Alice Smith",
            listOf("alice@example.com", "alice.work@example.com"),
            listOf("+1 555-0101", "+1 555-0202"),
            "Acme Corp",
            "123 Main St",
            "Engineering"
        )
        createServerContact(
            "Bob Johnson",
            listOf("bob@example.com"),
            listOf("+1 555-0102"),
            "Globex",
            "456 Elm St",
            "Sales"
        )
        createServerContact(
            "Charlie Brown",
            listOf("charlie@example.com"),
            listOf("+1 555-0103"),
            "Initech",
            "789 Oak St",
            "HR"
        )
    }

    override fun softDelete(syncId: String) {
        primaryDsl.update(CONTACTS)
            .set(CONTACTS.DELETED, true)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun restore(syncId: String) {
        primaryDsl.update(CONTACTS)
            .set(CONTACTS.DELETED, false)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun updateContact(contact: StoredContact): StoredContact {
        val rows = primaryDsl.update(CONTACTS)
            .set(CONTACTS.NAME, contact.name)
            .set(CONTACTS.COMPANY, contact.company)
            .set(CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
            .set(CONTACTS.DEPARTMENT, contact.department)
            .set(CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .set(CONTACTS.DIRTY, contact.dirty)
            .set(CONTACTS.DELETED, contact.deleted)
            .set(CONTACTS.VERSION, contact.version + 1)
            .where(CONTACTS.SYNC_ID.eq(contact.syncId))
            .and(CONTACTS.VERSION.eq(contact.version))
            .execute()

        check(rows != 0) { "Optimistic locking failure for contact ${contact.syncId}" }
        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncContact) {
        val syncConflictField = CONTACTS.field("SYNC_CONFLICT", String::class.java)
        if (syncConflictField != null) {
            val json = Jackson.asFormatString(serverVersion)
            primaryDsl.update(CONTACTS)
                .set(syncConflictField, json)
                .where(CONTACTS.SYNC_ID.eq(syncId))
                .execute()
        }
    }

    override fun resolveConflict(syncId: String, resolvedContact: StoredContact) {
        val syncConflictField = CONTACTS.field("SYNC_CONFLICT", String::class.java)

        primaryDsl.update(CONTACTS)
            .set(CONTACTS.NAME, resolvedContact.name)
            .set(CONTACTS.COMPANY, resolvedContact.company)
            .set(CONTACTS.COMPANY_ADDRESS, resolvedContact.companyAddress)
            .set(CONTACTS.DEPARTMENT, resolvedContact.department)
            .set(CONTACTS.UPDATED_AT_EPOCH_MS, resolvedContact.updatedAtEpochMs)
            .set(CONTACTS.DIRTY, resolvedContact.dirty)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .let { if (syncConflictField != null) it.setNull(syncConflictField) else it }
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    private fun insertContact(
        name: String, 
        emails: List<String>, 
        phones: List<String>, 
        company: String, 
        companyAddress: String, 
        department: String, 
        dirty: Boolean
    ): StoredContact {
        val syncId = UUID.randomUUID().toString()

        primaryDsl
            .insertInto(CONTACTS)
            .set(CONTACTS.SYNC_ID, syncId)
            .set(CONTACTS.NAME, name)
            .set(CONTACTS.COMPANY, company)
            .set(CONTACTS.COMPANY_ADDRESS, companyAddress)
            .set(CONTACTS.DEPARTMENT, department)
            .set(CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
            .set(CONTACTS.DIRTY, dirty)
            .set(CONTACTS.DELETED, false)
            .set(CONTACTS.VERSION, 1L)
            .execute()
        return requireNotNull(findBySyncId(syncId))
    }

    private fun toStoredContact(record: Record?): StoredContact {
        if (record == null) {
            return StoredContact(
                syncId = "unknown",
                name = "unknown",
                emails = emptyList(),
                phones = emptyList(),
                company = "",
                companyAddress = "",
                department = "",
                updatedAtEpochMs = 0L,
                dirty = false,
                deleted = false,
                version = 1L,
                syncConflict = null
            )
        }
        val syncConflictField = CONTACTS.field("SYNC_CONFLICT", String::class.java)
        return StoredContact(
            syncId = record.get(CONTACTS.SYNC_ID) ?: "unknown",
            name = record.get(CONTACTS.NAME) ?: "unknown",
            emails = record.get(emailsField),
            phones = record.get(phonesField),
            company = record.get(CONTACTS.COMPANY) ?: "",
            companyAddress = record.get(CONTACTS.COMPANY_ADDRESS) ?: "",
            department = record.get(CONTACTS.DEPARTMENT) ?: "",
            updatedAtEpochMs = record.get(CONTACTS.UPDATED_AT_EPOCH_MS) ?: 0L,
            dirty = record.get(CONTACTS.DIRTY) ?: false,
            deleted = record.get(CONTACTS.DELETED) ?: false,
            version = record.get(CONTACTS.VERSION) ?: 1L,
            syncConflict = if (syncConflictField != null) record.get(syncConflictField) else null
        )
    }

    companion object {
        private const val lastSyncStateKey = "last_contact_sync_epoch_ms"

        private val emailsField = DSL.multiset(
            DSL.select(CONTACT_EMAILS.EMAIL)
                .from(CONTACT_EMAILS)
                .where(CONTACT_EMAILS.CONTACT_ID.eq(CONTACTS.ID))
        ).`as`("EMAILS").convertFrom { r -> r.map { it.value1() } }

        private val phonesField = DSL.multiset(
            DSL.select(CONTACT_PHONES.PHONE)
                .from(CONTACT_PHONES)
                .where(CONTACT_PHONES.CONTACT_ID.eq(CONTACTS.ID))
        ).`as`("PHONES").convertFrom { r -> r.map { it.value1() } }

        private val allFields = CONTACTS.fields().toList() + emailsField + phonesField
    }
}
