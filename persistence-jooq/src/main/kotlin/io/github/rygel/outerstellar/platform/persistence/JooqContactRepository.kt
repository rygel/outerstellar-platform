package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_CONTACTS
import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_CONTACT_EMAILS
import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_CONTACT_PHONES
import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_CONTACT_SOCIALS
import io.github.rygel.outerstellar.platform.jooq.tables.references.PLT_SYNC_STATE
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.OptimisticLockException
import io.github.rygel.outerstellar.platform.model.StoredContact
import io.github.rygel.outerstellar.platform.sync.SyncContact
import java.util.UUID
import org.http4k.format.Jackson
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.DSL.using
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class JooqContactRepository(private val dsl: DSLContext) : ContactRepository {
    private val logger = LoggerFactory.getLogger(JooqContactRepository::class.java)

    private fun getFilterConditions(query: String?, includeDeleted: Boolean = false): Condition {
        var condition = if (includeDeleted) PLT_CONTACTS.DELETED.eq(true) else PLT_CONTACTS.DELETED.eq(false)

        if (!query.isNullOrBlank()) {
            condition =
                condition.and(
                    PLT_CONTACTS.NAME.containsIgnoreCase(query).or(PLT_CONTACTS.COMPANY.containsIgnoreCase(query))
                )
        }

        return condition
    }

    override fun listContacts(query: String?, limit: Int, offset: Int, includeDeleted: Boolean): List<ContactSummary> {
        val results =
            dsl.select(
                    PLT_CONTACTS.ID,
                    PLT_CONTACTS.SYNC_ID,
                    PLT_CONTACTS.NAME,
                    emailsField,
                    phonesField,
                    socialsField,
                    PLT_CONTACTS.COMPANY,
                    PLT_CONTACTS.COMPANY_ADDRESS,
                    PLT_CONTACTS.DEPARTMENT,
                    PLT_CONTACTS.UPDATED_AT_EPOCH_MS,
                    PLT_CONTACTS.DIRTY,
                    PLT_CONTACTS.DELETED,
                    PLT_CONTACTS.VERSION,
                    PLT_CONTACTS.SYNC_CONFLICT,
                )
                .from(PLT_CONTACTS)
                .where(getFilterConditions(query, includeDeleted))
                .orderBy(PLT_CONTACTS.NAME.asc())
                .limit(limit)
                .offset(offset)
                .fetch(::toStoredContact)

        return results.map(StoredContact::toSummary)
    }

    override fun countContacts(query: String?, includeDeleted: Boolean): Long {
        return dsl.fetchCount(PLT_CONTACTS, getFilterConditions(query, includeDeleted)).toLong()
    }

    override fun listDirtyContacts(): List<StoredContact> =
        dsl.select(allFields).from(PLT_CONTACTS).where(PLT_CONTACTS.DIRTY.eq(true)).fetch(::toStoredContact)

    override fun findBySyncId(syncId: String): StoredContact? =
        dsl.select(allFields).from(PLT_CONTACTS).where(PLT_CONTACTS.SYNC_ID.eq(syncId)).fetchOne(::toStoredContact)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact> =
        dsl.select(allFields)
            .from(PLT_CONTACTS)
            .where(PLT_CONTACTS.UPDATED_AT_EPOCH_MS.gt(updatedAtEpochMs))
            .fetch(::toStoredContact)

    override fun createServerContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact = insertContact(name, emails, phones, socialMedia, company, companyAddress, department, false)

    override fun createLocalContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact = insertContact(name, emails, phones, socialMedia, company, companyAddress, department, true)

    override fun upsertSyncedContact(contact: SyncContact, dirty: Boolean): StoredContact {
        val existing = findBySyncId(contact.syncId)

        dsl.transaction { config ->
            val txDsl = using(config)
            if (existing == null) {
                val contactId =
                    txDsl
                        .insertInto(PLT_CONTACTS)
                        .set(PLT_CONTACTS.SYNC_ID, contact.syncId)
                        .set(PLT_CONTACTS.NAME, contact.name)
                        .set(PLT_CONTACTS.COMPANY, contact.company)
                        .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                        .set(PLT_CONTACTS.DEPARTMENT, contact.department)
                        .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, contact.updatedAtEpochMs)
                        .set(PLT_CONTACTS.DIRTY, dirty)
                        .set(PLT_CONTACTS.DELETED, contact.deleted)
                        .set(PLT_CONTACTS.VERSION, 1L)
                        .returning(PLT_CONTACTS.ID)
                        .fetchOne()
                        ?.get(PLT_CONTACTS.ID) ?: throw IllegalStateException("Failed to insert contact")
                insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
            } else {
                txDsl
                    .update(PLT_CONTACTS)
                    .set(PLT_CONTACTS.NAME, contact.name)
                    .set(PLT_CONTACTS.COMPANY, contact.company)
                    .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                    .set(PLT_CONTACTS.DEPARTMENT, contact.department)
                    .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, contact.updatedAtEpochMs)
                    .set(PLT_CONTACTS.DIRTY, dirty)
                    .set(PLT_CONTACTS.DELETED, contact.deleted)
                    .set(PLT_CONTACTS.VERSION, existing.version + 1)
                    .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                    .execute()

                val contactId =
                    txDsl
                        .select(PLT_CONTACTS.ID)
                        .from(PLT_CONTACTS)
                        .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                        .fetchOne(PLT_CONTACTS.ID)
                if (contactId != null) {
                    insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
                }
            }
        }

        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        dsl.update(PLT_CONTACTS).set(PLT_CONTACTS.DIRTY, false).where(PLT_CONTACTS.SYNC_ID.`in`(syncIds)).execute()
    }

    override fun getLastSyncEpochMs(): Long =
        dsl.select(PLT_SYNC_STATE.STATE_VALUE)
            .from(PLT_SYNC_STATE)
            .where(PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
            .fetchOne(PLT_SYNC_STATE.STATE_VALUE) ?: 0L

    override fun setLastSyncEpochMs(value: Long) {
        val hasState = dsl.fetchCount(PLT_SYNC_STATE, PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey)) > 0
        if (hasState) {
            dsl.update(PLT_SYNC_STATE)
                .set(PLT_SYNC_STATE.STATE_VALUE, value)
                .where(PLT_SYNC_STATE.STATE_KEY.eq(lastSyncStateKey))
                .execute()
        } else {
            dsl.insertInto(PLT_SYNC_STATE)
                .set(PLT_SYNC_STATE.STATE_KEY, lastSyncStateKey)
                .set(PLT_SYNC_STATE.STATE_VALUE, value)
                .execute()
        }
    }

    override fun seedContacts() {
        if (dsl.fetchCount(PLT_CONTACTS, PLT_CONTACTS.DELETED.eq(false)) > 0) return

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
        dsl.update(PLT_CONTACTS)
            .set(PLT_CONTACTS.DELETED, true)
            .set(PLT_CONTACTS.VERSION, PLT_CONTACTS.VERSION.plus(1))
            .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun restore(syncId: String) {
        dsl.update(PLT_CONTACTS)
            .set(PLT_CONTACTS.DELETED, false)
            .set(PLT_CONTACTS.VERSION, PLT_CONTACTS.VERSION.plus(1))
            .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
            .execute()
    }

    override fun updateContact(contact: StoredContact): StoredContact {
        dsl.transaction { config ->
            val txDsl = using(config)
            val rows =
                txDsl
                    .update(PLT_CONTACTS)
                    .set(PLT_CONTACTS.NAME, contact.name)
                    .set(PLT_CONTACTS.COMPANY, contact.company)
                    .set(PLT_CONTACTS.COMPANY_ADDRESS, contact.companyAddress)
                    .set(PLT_CONTACTS.DEPARTMENT, contact.department)
                    .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                    .set(PLT_CONTACTS.DIRTY, contact.dirty)
                    .set(PLT_CONTACTS.DELETED, contact.deleted)
                    .set(PLT_CONTACTS.VERSION, contact.version + 1)
                    .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                    .and(PLT_CONTACTS.VERSION.eq(contact.version))
                    .execute()

            if (rows == 0) throw OptimisticLockException("Contact", contact.syncId)

            val contactId =
                txDsl
                    .select(PLT_CONTACTS.ID)
                    .from(PLT_CONTACTS)
                    .where(PLT_CONTACTS.SYNC_ID.eq(contact.syncId))
                    .fetchOne(PLT_CONTACTS.ID)
            if (contactId != null) {
                insertCollections(txDsl, contactId, contact.emails, contact.phones, contact.socialMedia)
            }
        }

        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncContact) {
        val json = Jackson.asFormatString(serverVersion)
        dsl.update(PLT_CONTACTS).set(PLT_CONTACTS.SYNC_CONFLICT, json).where(PLT_CONTACTS.SYNC_ID.eq(syncId)).execute()
    }

    override fun resolveConflict(syncId: String, resolvedContact: StoredContact) {
        dsl.transaction { config ->
            val txDsl = using(config)
            txDsl
                .update(PLT_CONTACTS)
                .set(PLT_CONTACTS.NAME, resolvedContact.name)
                .set(PLT_CONTACTS.COMPANY, resolvedContact.company)
                .set(PLT_CONTACTS.COMPANY_ADDRESS, resolvedContact.companyAddress)
                .set(PLT_CONTACTS.DEPARTMENT, resolvedContact.department)
                .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, resolvedContact.updatedAtEpochMs)
                .set(PLT_CONTACTS.DIRTY, resolvedContact.dirty)
                .set(PLT_CONTACTS.VERSION, PLT_CONTACTS.VERSION.plus(1))
                .setNull(PLT_CONTACTS.SYNC_CONFLICT)
                .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
                .execute()

            val contactId =
                txDsl
                    .select(PLT_CONTACTS.ID)
                    .from(PLT_CONTACTS)
                    .where(PLT_CONTACTS.SYNC_ID.eq(syncId))
                    .fetchOne(PLT_CONTACTS.ID)
            if (contactId != null) {
                insertCollections(
                    txDsl,
                    contactId,
                    resolvedContact.emails,
                    resolvedContact.phones,
                    resolvedContact.socialMedia,
                )
            }
        }
    }

    private fun insertCollections(
        txDsl: DSLContext,
        contactId: Long,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
    ) {
        txDsl.deleteFrom(PLT_CONTACT_EMAILS).where(PLT_CONTACT_EMAILS.CONTACT_ID.eq(contactId)).execute()
        if (emails.isNotEmpty()) {
            val emailInserts =
                emails.map { email ->
                    txDsl
                        .insertInto(PLT_CONTACT_EMAILS)
                        .set(PLT_CONTACT_EMAILS.CONTACT_ID, contactId)
                        .set(PLT_CONTACT_EMAILS.EMAIL, email)
                }
            txDsl.batch(emailInserts).execute()
        }

        txDsl.deleteFrom(PLT_CONTACT_PHONES).where(PLT_CONTACT_PHONES.CONTACT_ID.eq(contactId)).execute()
        if (phones.isNotEmpty()) {
            val phoneInserts =
                phones.map { phone ->
                    txDsl
                        .insertInto(PLT_CONTACT_PHONES)
                        .set(PLT_CONTACT_PHONES.CONTACT_ID, contactId)
                        .set(PLT_CONTACT_PHONES.PHONE, phone)
                }
            txDsl.batch(phoneInserts).execute()
        }

        txDsl.deleteFrom(PLT_CONTACT_SOCIALS).where(PLT_CONTACT_SOCIALS.CONTACT_ID.eq(contactId)).execute()
        if (socialMedia.isNotEmpty()) {
            val socialInserts =
                socialMedia.map { social ->
                    txDsl
                        .insertInto(PLT_CONTACT_SOCIALS)
                        .set(PLT_CONTACT_SOCIALS.CONTACT_ID, contactId)
                        .set(PLT_CONTACT_SOCIALS.SOCIAL_MEDIA, social)
                }
            txDsl.batch(socialInserts).execute()
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

        dsl.transaction { config ->
            val txDsl = using(config)
            val contactId =
                txDsl
                    .insertInto(PLT_CONTACTS)
                    .set(PLT_CONTACTS.SYNC_ID, syncId)
                    .set(PLT_CONTACTS.NAME, name)
                    .set(PLT_CONTACTS.COMPANY, company)
                    .set(PLT_CONTACTS.COMPANY_ADDRESS, companyAddress)
                    .set(PLT_CONTACTS.DEPARTMENT, department)
                    .set(PLT_CONTACTS.UPDATED_AT_EPOCH_MS, System.currentTimeMillis())
                    .set(PLT_CONTACTS.DIRTY, dirty)
                    .set(PLT_CONTACTS.DELETED, false)
                    .set(PLT_CONTACTS.VERSION, 1L)
                    .returning(PLT_CONTACTS.ID)
                    .fetchOne()
                    ?.get(PLT_CONTACTS.ID) ?: throw IllegalStateException("Failed to insert contact")

            insertCollections(txDsl, contactId, emails, phones, socialMedia)
        }

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
            syncId = record.get(PLT_CONTACTS.SYNC_ID) ?: "unknown",
            name = record.get(PLT_CONTACTS.NAME) ?: "unknown",
            emails = record.get(emailsField) ?: emptyList(),
            phones = record.get(phonesField) ?: emptyList(),
            socialMedia = record.get(socialsField) ?: emptyList(),
            company = record.get(PLT_CONTACTS.COMPANY) ?: "",
            companyAddress = record.get(PLT_CONTACTS.COMPANY_ADDRESS) ?: "",
            department = record.get(PLT_CONTACTS.DEPARTMENT) ?: "",
            updatedAtEpochMs = record.get(PLT_CONTACTS.UPDATED_AT_EPOCH_MS) ?: 0L,
            dirty = record.get(PLT_CONTACTS.DIRTY) ?: false,
            deleted = record.get(PLT_CONTACTS.DELETED) ?: false,
            version = record.get(PLT_CONTACTS.VERSION) ?: 1L,
            syncConflict = record.get(PLT_CONTACTS.SYNC_CONFLICT),
        )
    }

    companion object {
        private const val lastSyncStateKey = "last_contact_sync_epoch_ms"

        private val emailsField =
            DSL.multiset(
                    DSL.select(PLT_CONTACT_EMAILS.EMAIL)
                        .from(PLT_CONTACT_EMAILS)
                        .where(PLT_CONTACT_EMAILS.CONTACT_ID.eq(PLT_CONTACTS.ID))
                )
                .`as`("EMAILS")
                .convertFrom { r -> r.map { it.value1() } }

        private val phonesField =
            DSL.multiset(
                    DSL.select(PLT_CONTACT_PHONES.PHONE)
                        .from(PLT_CONTACT_PHONES)
                        .where(PLT_CONTACT_PHONES.CONTACT_ID.eq(PLT_CONTACTS.ID))
                )
                .`as`("PHONES")
                .convertFrom { r -> r.map { it.value1() } }

        private val socialsField =
            DSL.multiset(
                    DSL.select(PLT_CONTACT_SOCIALS.SOCIAL_MEDIA)
                        .from(PLT_CONTACT_SOCIALS)
                        .where(PLT_CONTACT_SOCIALS.CONTACT_ID.eq(PLT_CONTACTS.ID))
                )
                .`as`("SOCIAL_MEDIA")
                .convertFrom { r -> r.map { it.value1() } }

        private val allFields = PLT_CONTACTS.fields().toList() + emailsField + phonesField + socialsField
    }
}
