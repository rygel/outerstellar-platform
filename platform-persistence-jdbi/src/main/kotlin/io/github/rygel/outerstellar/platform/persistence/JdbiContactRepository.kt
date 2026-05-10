package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.OptimisticLockException
import io.github.rygel.outerstellar.platform.model.StoredContact
import io.github.rygel.outerstellar.platform.sync.SyncContact
import java.util.UUID
import org.http4k.format.KotlinxSerialization
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class JdbiContactRepository(private val jdbi: Jdbi) : ContactRepository {
    private val logger = LoggerFactory.getLogger(JdbiContactRepository::class.java)

    override fun listContacts(query: String?, limit: Int, offset: Int, includeDeleted: Boolean): List<ContactSummary> {
        return jdbi.withHandle<List<ContactSummary>, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(query, includeDeleted)
            val sql =
                """
                SELECT * FROM plt_contacts
                WHERE $whereClause
                ORDER BY name ASC
                LIMIT :limit OFFSET :offset
                """
            val contacts =
                handle
                    .createQuery(sql)
                    .also { bindings(it) }
                    .bind("limit", limit)
                    .bind("offset", offset)
                    .map { rs, _ -> readContactRow(rs) }
                    .list()
            val contactIds = contacts.map { it.id }
            val emailsByContact = loadEmailsForContacts(handle, contactIds)
            val phonesByContact = loadPhonesForContacts(handle, contactIds)
            val socialsByContact = loadSocialsForContacts(handle, contactIds)
            contacts.map { mapContact(it, emailsByContact, phonesByContact, socialsByContact).toSummary() }
        }
    }

    override fun countContacts(query: String?, includeDeleted: Boolean): Long {
        return jdbi.withHandle<Long, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(query, includeDeleted)
            val q = handle.createQuery("SELECT COUNT(*) FROM plt_contacts WHERE $whereClause")
            bindings(q)
            q.mapTo(Long::class.java).one()
        }
    }

    override fun listDirtyContacts(): List<StoredContact> =
        jdbi.withHandle<List<StoredContact>, Exception> { handle ->
            val contacts =
                handle
                    .createQuery("SELECT * FROM plt_contacts WHERE dirty = true")
                    .map { rs, _ -> readContactRow(rs) }
                    .list()
            val contactIds = contacts.map { it.id }
            val emailsByContact = loadEmailsForContacts(handle, contactIds)
            val phonesByContact = loadPhonesForContacts(handle, contactIds)
            val socialsByContact = loadSocialsForContacts(handle, contactIds)
            contacts.map { mapContact(it, emailsByContact, phonesByContact, socialsByContact) }
        }

    override fun findBySyncId(syncId: String): StoredContact? =
        jdbi.withHandle<StoredContact?, Exception> { handle -> findBySyncId(handle, syncId) }

    private fun findBySyncId(handle: Handle, syncId: String): StoredContact? {
        val contact =
            handle
                .createQuery("SELECT * FROM plt_contacts WHERE sync_id = :syncId")
                .bind("syncId", syncId)
                .map { rs, _ -> readContactRow(rs) }
                .findOne()
                .orElse(null) ?: return null
        val emailsByContact = loadEmailsForContacts(handle, listOf(contact.id))
        val phonesByContact = loadPhonesForContacts(handle, listOf(contact.id))
        val socialsByContact = loadSocialsForContacts(handle, listOf(contact.id))
        return mapContact(contact, emailsByContact, phonesByContact, socialsByContact)
    }

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact> =
        jdbi.withHandle<List<StoredContact>, Exception> { handle ->
            val contacts =
                handle
                    .createQuery("SELECT * FROM plt_contacts WHERE updated_at_epoch_ms > :since")
                    .bind("since", updatedAtEpochMs)
                    .map { rs, _ -> readContactRow(rs) }
                    .list()
            val contactIds = contacts.map { it.id }
            val emailsByContact = loadEmailsForContacts(handle, contactIds)
            val phonesByContact = loadPhonesForContacts(handle, contactIds)
            val socialsByContact = loadSocialsForContacts(handle, contactIds)
            contacts.map { mapContact(it, emailsByContact, phonesByContact, socialsByContact) }
        }

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
        jdbi.useTransaction<Exception> { handle ->
            val existing = findBySyncId(handle, contact.syncId)
            if (existing == null) {
                val contactId =
                    handle
                        .createUpdate(
                            """
                            INSERT INTO plt_contacts
                                (sync_id, name, company, company_address, department,
                                 updated_at_epoch_ms, dirty, deleted, version)
                            VALUES (:syncId, :name, :company, :companyAddress, :department,
                                    :updatedAtEpochMs, :dirty, :deleted, 1)
                            """
                        )
                        .bind("syncId", contact.syncId)
                        .bind("name", contact.name)
                        .bind("company", contact.company)
                        .bind("companyAddress", contact.companyAddress)
                        .bind("department", contact.department)
                        .bind("updatedAtEpochMs", contact.updatedAtEpochMs)
                        .bind("dirty", dirty)
                        .bind("deleted", contact.deleted)
                        .executeAndReturnGeneratedKeys("id")
                        .mapTo(Long::class.java)
                        .one()
                insertCollections(handle, contactId, contact.emails, contact.phones, contact.socialMedia)
            } else {
                handle
                    .createUpdate(
                        """
                        UPDATE plt_contacts
                        SET name = :name, company = :company, company_address = :companyAddress,
                            department = :department, updated_at_epoch_ms = :updatedAtEpochMs,
                            dirty = :dirty, deleted = :deleted, version = :version
                        WHERE sync_id = :syncId
                        """
                    )
                    .bind("syncId", contact.syncId)
                    .bind("name", contact.name)
                    .bind("company", contact.company)
                    .bind("companyAddress", contact.companyAddress)
                    .bind("department", contact.department)
                    .bind("updatedAtEpochMs", contact.updatedAtEpochMs)
                    .bind("dirty", dirty)
                    .bind("deleted", contact.deleted)
                    .bind("version", existing.version + 1)
                    .execute()

                val contactId = getContactId(handle, contact.syncId)
                if (contactId != null) {
                    insertCollections(handle, contactId, contact.emails, contact.phones, contact.socialMedia)
                }
            }
        }
        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        jdbi.useHandle<Exception> { handle ->
            val batch = handle.prepareBatch("UPDATE plt_contacts SET dirty = false WHERE sync_id = :syncId")
            syncIds.forEach { batch.bind("syncId", it).add() }
            batch.execute()
        }
    }

    override fun getLastSyncEpochMs(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT state_value FROM plt_sync_state WHERE state_key = :key")
                .bind("key", LAST_SYNC_STATE_KEY)
                .mapTo(Long::class.java)
                .findOne()
                .orElse(0L)
        }

    override fun setLastSyncEpochMs(value: Long) {
        jdbi.useHandle<Exception> { handle ->
            val updated =
                handle
                    .createUpdate("UPDATE plt_sync_state SET state_value = :value WHERE state_key = :key")
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            if (updated == 0) {
                handle
                    .createUpdate("INSERT INTO plt_sync_state (state_key, state_value) VALUES (:key, :value)")
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            }
        }
    }

    override fun seedContacts() {
        val count =
            jdbi.withHandle<Long, Exception> { handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM plt_contacts WHERE deleted = false")
                    .mapTo(Long::class.java)
                    .one()
            }
        if (count > 0) return

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
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_contacts SET deleted = true, version = version + 1 WHERE sync_id = :syncId")
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun restore(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_contacts SET deleted = false, version = version + 1 WHERE sync_id = :syncId")
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun updateContact(contact: StoredContact): StoredContact {
        jdbi.useTransaction<Exception> { handle ->
            val rows =
                handle
                    .createUpdate(
                        """
                        UPDATE plt_contacts
                        SET name = :name, company = :company, company_address = :companyAddress,
                            department = :department, updated_at_epoch_ms = :updatedAtEpochMs,
                            dirty = :dirty, deleted = :deleted, version = :newVersion
                        WHERE sync_id = :syncId AND version = :version
                        """
                    )
                    .bind("name", contact.name)
                    .bind("company", contact.company)
                    .bind("companyAddress", contact.companyAddress)
                    .bind("department", contact.department)
                    .bind("updatedAtEpochMs", System.currentTimeMillis())
                    .bind("dirty", contact.dirty)
                    .bind("deleted", contact.deleted)
                    .bind("newVersion", contact.version + 1)
                    .bind("syncId", contact.syncId)
                    .bind("version", contact.version)
                    .execute()

            if (rows == 0) throw OptimisticLockException("Contact", contact.syncId)

            val contactId = getContactId(handle, contact.syncId)
            if (contactId != null) {
                insertCollections(handle, contactId, contact.emails, contact.phones, contact.socialMedia)
            }
        }
        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncContact) {
        val json = KotlinxSerialization.asFormatString(serverVersion)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_contacts SET sync_conflict = :json WHERE sync_id = :syncId")
                .bind("json", json)
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun resolveConflict(syncId: String, resolvedContact: StoredContact) {
        jdbi.useTransaction<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE plt_contacts
                    SET name = :name, company = :company, company_address = :companyAddress,
                        department = :department, updated_at_epoch_ms = :updatedAtEpochMs,
                        dirty = :dirty, version = version + 1, sync_conflict = NULL
                    WHERE sync_id = :syncId
                    """
                )
                .bind("name", resolvedContact.name)
                .bind("company", resolvedContact.company)
                .bind("companyAddress", resolvedContact.companyAddress)
                .bind("department", resolvedContact.department)
                .bind("updatedAtEpochMs", resolvedContact.updatedAtEpochMs)
                .bind("dirty", resolvedContact.dirty)
                .bind("syncId", syncId)
                .execute()

            val contactId = getContactId(handle, syncId)
            if (contactId != null) {
                insertCollections(
                    handle,
                    contactId,
                    resolvedContact.emails,
                    resolvedContact.phones,
                    resolvedContact.socialMedia,
                )
            }
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

        jdbi.useTransaction<Exception> { handle ->
            val contactId =
                handle
                    .createUpdate(
                        """
                        INSERT INTO plt_contacts
                            (sync_id, name, company, company_address, department,
                             updated_at_epoch_ms, dirty, deleted, version)
                        VALUES (:syncId, :name, :company, :companyAddress, :department,
                                :updatedAtEpochMs, :dirty, false, 1)
                        """
                    )
                    .bind("syncId", syncId)
                    .bind("name", name)
                    .bind("company", company)
                    .bind("companyAddress", companyAddress)
                    .bind("department", department)
                    .bind("updatedAtEpochMs", System.currentTimeMillis())
                    .bind("dirty", dirty)
                    .executeAndReturnGeneratedKeys("id")
                    .mapTo(Long::class.java)
                    .one()

            insertCollections(handle, contactId, emails, phones, socialMedia)
        }

        return requireNotNull(findBySyncId(syncId))
    }

    private fun insertCollections(
        handle: Handle,
        contactId: Long,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
    ) {
        handle.createUpdate("DELETE FROM plt_contact_emails WHERE contact_id = :id").bind("id", contactId).execute()
        if (emails.isNotEmpty()) {
            val batch =
                handle.prepareBatch("INSERT INTO plt_contact_emails (contact_id, email) VALUES (:contactId, :email)")
            emails.forEach { batch.bind("contactId", contactId).bind("email", it).add() }
            batch.execute()
        }

        handle.createUpdate("DELETE FROM plt_contact_phones WHERE contact_id = :id").bind("id", contactId).execute()
        if (phones.isNotEmpty()) {
            val batch =
                handle.prepareBatch("INSERT INTO plt_contact_phones (contact_id, phone) VALUES (:contactId, :phone)")
            phones.forEach { batch.bind("contactId", contactId).bind("phone", it).add() }
            batch.execute()
        }

        handle.createUpdate("DELETE FROM plt_contact_socials WHERE contact_id = :id").bind("id", contactId).execute()
        if (socialMedia.isNotEmpty()) {
            val batch =
                handle.prepareBatch(
                    "INSERT INTO plt_contact_socials (contact_id, social_media) VALUES (:contactId, :socialMedia)"
                )
            socialMedia.forEach { batch.bind("contactId", contactId).bind("socialMedia", it).add() }
            batch.execute()
        }
    }

    private fun getContactId(handle: Handle, syncId: String): Long? =
        handle
            .createQuery("SELECT id FROM plt_contacts WHERE sync_id = :syncId")
            .bind("syncId", syncId)
            .mapTo(Long::class.java)
            .findOne()
            .orElse(null)

    private data class FilterClause(val sql: String, val binder: (org.jdbi.v3.core.statement.Query) -> Unit)

    private fun buildFilterClause(query: String?, includeDeleted: Boolean): FilterClause {
        val conditions = mutableListOf<String>()
        val bindings = mutableMapOf<String, Any>()

        conditions.add(if (includeDeleted) "deleted = true" else "deleted = false")

        if (!query.isNullOrBlank()) {
            conditions.add("(LOWER(name) LIKE :likeQuery ESCAPE '!' OR LOWER(company) LIKE :likeQuery ESCAPE '!')")
            bindings["likeQuery"] = "%${query.lowercase().escapeLike()}%"
        }

        val whereClause = conditions.joinToString(" AND ")
        return FilterClause(whereClause) { q -> bindings.forEach { (key, value) -> q.bind(key, value) } }
    }

    private data class ContactRowData(
        val id: Long,
        val syncId: String,
        val name: String,
        val company: String,
        val companyAddress: String,
        val department: String,
        val updatedAtEpochMs: Long,
        val dirty: Boolean,
        val deleted: Boolean,
        val version: Long,
        val syncConflict: String?,
    )

    private fun readContactRow(rs: java.sql.ResultSet): ContactRowData =
        ContactRowData(
            id = rs.getLong("id"),
            syncId = rs.getString("sync_id") ?: "unknown",
            name = rs.getString("name") ?: "unknown",
            company = rs.getString("company") ?: "",
            companyAddress = rs.getString("company_address") ?: "",
            department = rs.getString("department") ?: "",
            updatedAtEpochMs = rs.getLong("updated_at_epoch_ms"),
            dirty = rs.getBoolean("dirty"),
            deleted = rs.getBoolean("deleted"),
            version = rs.getLong("version"),
            syncConflict = rs.getString("sync_conflict"),
        )

    private fun mapContact(
        row: ContactRowData,
        emailsByContact: Map<Long, List<String>>,
        phonesByContact: Map<Long, List<String>>,
        socialsByContact: Map<Long, List<String>>,
    ): StoredContact =
        StoredContact(
            syncId = row.syncId,
            name = row.name,
            emails = emailsByContact[row.id] ?: emptyList(),
            phones = phonesByContact[row.id] ?: emptyList(),
            socialMedia = socialsByContact[row.id] ?: emptyList(),
            company = row.company,
            companyAddress = row.companyAddress,
            department = row.department,
            updatedAtEpochMs = row.updatedAtEpochMs,
            dirty = row.dirty,
            deleted = row.deleted,
            version = row.version,
            syncConflict = row.syncConflict,
        )

    private fun loadEmailsForContacts(handle: Handle, contactIds: Collection<Long>): Map<Long, List<String>> {
        if (contactIds.isEmpty()) return emptyMap()
        return handle
            .createQuery("SELECT contact_id, email FROM plt_contact_emails WHERE contact_id IN (<ids>)")
            .bindList("ids", contactIds)
            .map { rs, _ -> rs.getLong("contact_id") to rs.getString("email")!! }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    private fun loadPhonesForContacts(handle: Handle, contactIds: Collection<Long>): Map<Long, List<String>> {
        if (contactIds.isEmpty()) return emptyMap()
        return handle
            .createQuery("SELECT contact_id, phone FROM plt_contact_phones WHERE contact_id IN (<ids>)")
            .bindList("ids", contactIds)
            .map { rs, _ -> rs.getLong("contact_id") to rs.getString("phone")!! }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    private fun loadSocialsForContacts(handle: Handle, contactIds: Collection<Long>): Map<Long, List<String>> {
        if (contactIds.isEmpty()) return emptyMap()
        return handle
            .createQuery("SELECT contact_id, social_media FROM plt_contact_socials WHERE contact_id IN (<ids>)")
            .bindList("ids", contactIds)
            .map { rs, _ -> rs.getLong("contact_id") to rs.getString("social_media")!! }
            .list()
            .groupBy({ it.first }, { it.second })
    }

    companion object {
        private const val LAST_SYNC_STATE_KEY = "last_contact_sync_epoch_ms"
    }
}

private fun String.escapeLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")
