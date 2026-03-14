package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.jooq.tables.references.CONTACTS
import dev.outerstellar.starter.jooq.tables.references.CONTACT_EMAILS
import dev.outerstellar.starter.jooq.tables.references.CONTACT_PHONES
import dev.outerstellar.starter.jooq.tables.references.CONTACT_SOCIALS
import dev.outerstellar.starter.jooq.tables.references.SYNC_STATE
import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.OptimisticLockException
import dev.outerstellar.starter.model.StoredContact
import dev.outerstellar.starter.sync.SyncContact
import java.util.UUID
import org.http4k.format.Jackson
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class JooqContactRepository(
    private val dsl: DSLContext,
) : ContactRepository {
    private val logger = LoggerFactory.getLogger(JooqContactRepository::class.java)

    private fun getFilterConditions(query: String?, includeDeleted: Boolean = false): Condition {
        var condition = if (includeDeleted) CONTACTS.DELETED.eq(true) else CONTACTS.DELETED.eq(false)

        if (!query.isNullOrBlank()) {
            condition =
                condition.and(
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
        includeDeleted: Boolean,
    ): List<ContactSummary> {
        val results =
            dsl
                .select(
                    CONTACTS.ID,
                    CONTACTS.SYNC_ID,
                    CONTACTS.NAME,
                    emailsField,
                    phonesField,
                    socialsField,
                    CONTACTS.COMPANY,
                    CONTACTS.COMPANY_ADDRESS,
                    CONTACTS.DEPARTMENT,
                    CONTACTS.UPDATED_AT_EPOCH_MS,
                    CONTACTS.DIRTY,
                    CONTACTS.DELETED,
                    CONTACTS.VERSION,
                    CONTACTS.SYNC_CONFLICT,
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
        return dsl.fetchCount(CONTACTS, getFilterConditions(query, includeDeleted)).toLong()
    }

    override fun listDirtyContacts(): List<StoredContact> =
        dsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.DIRTY.eq(true))
            .fetch(::toStoredContact)

    override fun findBySyncId(syncId: String): StoredContact? =
        dsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .fetchOne(::toStoredContact)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact> =
        dsl
            .select(allFields)
            .from(CONTACTS)
            .where(CONTACTS.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs))
            .fetch(::toStoredContact)

    override fun createServerContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact =
        insertContact(name, emails, phones, socialMedia, company, companyAddress, department, false)

    override fun createLocalContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact =
        insertContact(name, emails, phones, socialMedia, company, companyAddress, department, true)

    override fun upsertSyncedContact(contact: SyncContact, dirty: Boolean): StoredContact {
        val existing = findBySyncId(contact.syncId)

        if (existing == null) {
            val contactId =
                dsl
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
                    .returning(CONTACTS.ID)
                    .fetchOne()
                    ?.get(CONTACTS.ID) ?: throw IllegalStateException("Failed to insert contact")
            insertCollections(contactId, contact.emails, contact.phones, contact.socialMedia)
        } else {
            dsl
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

            val contactId =
                dsl
                    .select(CONTACTS.ID)
                    .from(CONTACTS)
                    .where(CONTACTS.SYNC_ID.eq(contact.syncId))
                    .fetchOne(CONTACTS.ID)
            if (contactId != null) {
                insertCollections(contactId, contact.emails, contact.phones, contact.socialMedia)
            }
        }

        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        dsl
            .update(CONTACTS)
            .set(CONTACTS.DIRTY, false)
            .where(CONTACTS.SYNC_ID.`in`(syncIds))
            .execute()
    }

    override fun getLastSyncEpochMs(): Long =
        dsl
            .select(SYNC_STATE.STATE_VALUE)
            .from(SYNC_STATE)
            .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
            .fetchOne(SYNC_STATE.STATE_VALUE) ?: 0L

    override fun setLastSyncEpochMs(value: Long) {
        val hasState =
            dsl.fetchCount(SYNC_STATE, SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
        if (hasState) {
            dsl
                .update(SYNC_STATE)
                .set(SYNC_STATE.STATE_VALUE, value)
                .where(SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
                .execute()
        } else {
            dsl
                .insertInto(SYNC_STATE)
                .set(SYNC_STATE.STATE_KEY, lastSyncStateKey)
                .set(SYNC_STATE.STATE_VALUE, value)
                .execute()
        }
    }

    override fun seedStarterContacts() {
        if (dsl.fetchCount(CONTACTS, CONTACTS.DELETED.eq(false)) > 0) return

        createServerContact(
            "Alice Smith",
            listOf("alice@example.com", "alice.work@example.com"),
            listOf("+1 555-0101", "+1 555-0202"),
            listOf("@alice_smith", "linkedin.com/in/alicesmith"),
            "Acme Corp",
            "123 Main St",
            "Engineering",
        )
        createServerContact(
            "Bob Johnson",
            listOf("bob@example.com"),
            listOf("+1 555-0102"),
            listOf("@bobjohnson"),
            "Globex",
            "456 Elm St",
            "Sales",
        )
        createServerContact(
            "Charlie Brown",
            listOf("charlie@example.com"),
            listOf("+1 555-0103"),
            emptyList(),
            "Initech",
            "789 Oak St",
            "HR",
        )
    }

    override fun softDelete(syncId: String) {
        dsl
            .update(CONTACTS)
            .set(CONTACTS.DELETED, true)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun restore(syncId: String) {
        dsl
            .update(CONTACTS)
            .set(CONTACTS.DELETED, false)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun updateContact(contact: StoredContact): StoredContact {
        val rows =
            dsl
                .update(CONTACTS)
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

        if (rows == 0) throw OptimisticLockException("Contact", contact.syncId)

        val contactId =
            dsl
                .select(CONTACTS.ID)
                .from(CONTACTS)
                .where(CONTACTS.SYNC_ID.eq(contact.syncId))
                .fetchOne(CONTACTS.ID)
        if (contactId != null) {
            insertCollections(contactId, contact.emails, contact.phones, contact.socialMedia)
        }

        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncContact) {
        val json = Jackson.asFormatString(serverVersion)
        dsl
            .update(CONTACTS)
            .set(CONTACTS.SYNC_CONFLICT, json)
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun resolveConflict(syncId: String, resolvedContact: StoredContact) {
        dsl
            .update(CONTACTS)
            .set(CONTACTS.NAME, resolvedContact.name)
            .set(CONTACTS.COMPANY, resolvedContact.company)
            .set(CONTACTS.COMPANY_ADDRESS, resolvedContact.companyAddress)
            .set(CONTACTS.DEPARTMENT, resolvedContact.department)
            .set(CONTACTS.UPDATED_AT_EPOCH_MS, resolvedContact.updatedAtEpochMs)
            .set(CONTACTS.DIRTY, resolvedContact.dirty)
            .set(CONTACTS.VERSION, CONTACTS.VERSION.plus(1))
            .setNull(CONTACTS.SYNC_CONFLICT)
            .where(CONTACTS.SYNC_ID.eq(syncId))
            .execute()

        val contactId =
            dsl
                .select(CONTACTS.ID)
                .from(CONTACTS)
                .where(CONTACTS.SYNC_ID.eq(syncId))
                .fetchOne(CONTACTS.ID)
        if (contactId != null) {
            insertCollections(
                contactId,
                resolvedContact.emails,
                resolvedContact.phones,
                resolvedContact.socialMedia,
            )
        }
    }

    private fun insertCollections(
        contactId: Long,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
    ) {
        dsl
            .deleteFrom(CONTACT_EMAILS)
            .where(CONTACT_EMAILS.CONTACT_ID.eq(contactId))
            .execute()
        emails.forEach { email ->
            dsl
                .insertInto(CONTACT_EMAILS)
                .set(CONTACT_EMAILS.CONTACT_ID, contactId)
                .set(CONTACT_EMAILS.EMAIL, email)
                .execute()
        }
        dsl
            .deleteFrom(CONTACT_PHONES)
            .where(CONTACT_PHONES.CONTACT_ID.eq(contactId))
            .execute()
        phones.forEach { phone ->
            dsl
                .insertInto(CONTACT_PHONES)
                .set(CONTACT_PHONES.CONTACT_ID, contactId)
                .set(CONTACT_PHONES.PHONE, phone)
                .execute()
        }
        dsl
            .deleteFrom(CONTACT_SOCIALS)
            .where(CONTACT_SOCIALS.CONTACT_ID.eq(contactId))
            .execute()
        socialMedia.forEach { social ->
            dsl
                .insertInto(CONTACT_SOCIALS)
                .set(CONTACT_SOCIALS.CONTACT_ID, contactId)
                .set(CONTACT_SOCIALS.SOCIAL_MEDIA, social)
                .execute()
        }
    }

    private fun insertContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
        dirty: Boolean,
    ): StoredContact {
        val syncId = UUID.randomUUID().toString()

        val contactId =
            dsl
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
                .returning(CONTACTS.ID)
                .fetchOne()
                ?.get(CONTACTS.ID) ?: throw IllegalStateException("Failed to insert contact")

        insertCollections(contactId, emails, phones, socialMedia)
        return requireNotNull(findBySyncId(syncId))
    }

    private fun toStoredContact(record: Record?): StoredContact {
        if (record == null) {
            return StoredContact(
                syncId = "unknown",
                name = "unknown",
                emails = emptyList(),
                phones = emptyList(),
                socialMedia = emptyList(),
                company = "",
                companyAddress = "",
                department = "",
                updatedAtEpochMs = 0L,
                dirty = false,
                deleted = false,
                version = 1L,
                syncConflict = null,
            )
        }
        return StoredContact(
            syncId = record.get(CONTACTS.SYNC_ID) ?: "unknown",
            name = record.get(CONTACTS.NAME) ?: "unknown",
            emails = record.get(emailsField) ?: emptyList(),
            phones = record.get(phonesField) ?: emptyList(),
            socialMedia = record.get(socialsField) ?: emptyList(),
            company = record.get(CONTACTS.COMPANY) ?: "",
            companyAddress = record.get(CONTACTS.COMPANY_ADDRESS) ?: "",
            department = record.get(CONTACTS.DEPARTMENT) ?: "",
            updatedAtEpochMs = record.get(CONTACTS.UPDATED_AT_EPOCH_MS) ?: 0L,
            dirty = record.get(CONTACTS.DIRTY) ?: false,
            deleted = record.get(CONTACTS.DELETED) ?: false,
            version = record.get(CONTACTS.VERSION) ?: 1L,
            syncConflict = record.get(CONTACTS.SYNC_CONFLICT),
        )
    }

    companion object {
        private const val lastSyncStateKey = "last_contact_sync_epoch_ms"

        private val emailsField =
            DSL.multiset(
                    DSL.select(CONTACT_EMAILS.EMAIL)
                        .from(CONTACT_EMAILS)
                        .where(CONTACT_EMAILS.CONTACT_ID.eq(CONTACTS.ID))
                )
                .`as`("EMAILS")
                .convertFrom { r -> r.map { it.value1() } }

        private val phonesField =
            DSL.multiset(
                    DSL.select(CONTACT_PHONES.PHONE)
                        .from(CONTACT_PHONES)
                        .where(CONTACT_PHONES.CONTACT_ID.eq(CONTACTS.ID))
                )
                .`as`("PHONES")
                .convertFrom { r -> r.map { it.value1() } }

        private val socialsField =
            DSL.multiset(
                    DSL.select(CONTACT_SOCIALS.SOCIAL_MEDIA)
                        .from(CONTACT_SOCIALS)
                        .where(CONTACT_SOCIALS.CONTACT_ID.eq(CONTACTS.ID))
                )
                .`as`("SOCIAL_MEDIA")
                .convertFrom { r -> r.map { it.value1() } }

        private val allFields =
            CONTACTS.fields().toList() + emailsField + phonesField + socialsField
    }
}
