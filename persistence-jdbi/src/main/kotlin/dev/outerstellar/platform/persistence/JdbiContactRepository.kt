package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.ContactSummary
import dev.outerstellar.platform.model.OptimisticLockException
import dev.outerstellar.platform.model.StoredContact
import dev.outerstellar.platform.sync.SyncContact
import java.util.UUID
import org.http4k.format.Jackson
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class JdbiContactRepository(private val jdbi: Jdbi) : ContactRepository {
    private val logger = LoggerFactory.getLogger(JdbiContactRepository::class.java)

    override fun listContacts(
        query: String?,
        limit: Int,
        offset: Int,
        includeDeleted: Boolean,
    ): List<ContactSummary> {
        return jdbi.withHandle<List<ContactSummary>, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(query, includeDeleted)
            val sql =
                """
                SELECT * FROM contacts
                WHERE $whereClause
                ORDER BY name ASC
                LIMIT :limit OFFSET :offset
                """
            val q = handle.createQuery(sql)
            bindings(q)
            q.bind("limit", limit)
                .bind("offset", offset)
                .map { rs, _ ->
                    val contactId = rs.getLong("id")
                    mapContact(rs, handle, contactId)
                }
                .list()
                .map(StoredContact::toSummary)
        }
    }

    override fun countContacts(query: String?, includeDeleted: Boolean): Long {
        return jdbi.withHandle<Long, Exception> { handle ->
            val (whereClause, bindings) = buildFilterClause(query, includeDeleted)
            val q = handle.createQuery("SELECT COUNT(*) FROM contacts WHERE $whereClause")
            bindings(q)
            q.mapTo(Long::class.java).one()
        }
    }

    override fun listDirtyContacts(): List<StoredContact> =
        jdbi.withHandle<List<StoredContact>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM contacts WHERE dirty = true")
                .map { rs, _ -> mapContact(rs, handle, rs.getLong("id")) }
                .list()
        }

    override fun findBySyncId(syncId: String): StoredContact? =
        jdbi.withHandle<StoredContact?, Exception> { handle -> findBySyncId(handle, syncId) }

    private fun findBySyncId(handle: Handle, syncId: String): StoredContact? =
        handle
            .createQuery("SELECT * FROM contacts WHERE sync_id = :syncId")
            .bind("syncId", syncId)
            .map { rs, _ -> mapContact(rs, handle, rs.getLong("id")) }
            .findOne()
            .orElse(null)

    override fun findChangesSince(updatedAtEpochMs: Long): List<StoredContact> =
        jdbi.withHandle<List<StoredContact>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM contacts WHERE updated_at_epoch_ms > :since")
                .bind("since", updatedAtEpochMs)
                .map { rs, _ -> mapContact(rs, handle, rs.getLong("id")) }
                .list()
        }

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
        jdbi.useTransaction<Exception> { handle ->
            val existing = findBySyncId(handle, contact.syncId)
            if (existing == null) {
                val contactId =
                    handle
                        .createUpdate(
                            """
                            INSERT INTO contacts
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
                insertCollections(
                    handle,
                    contactId,
                    contact.emails,
                    contact.phones,
                    contact.socialMedia,
                )
            } else {
                handle
                    .createUpdate(
                        """
                        UPDATE contacts
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
                    insertCollections(
                        handle,
                        contactId,
                        contact.emails,
                        contact.phones,
                        contact.socialMedia,
                    )
                }
            }
        }
        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markClean(syncIds: Collection<String>) {
        if (syncIds.isEmpty()) return
        jdbi.useHandle<Exception> { handle ->
            val batch =
                handle.prepareBatch("UPDATE contacts SET dirty = false WHERE sync_id = :syncId")
            syncIds.forEach { batch.bind("syncId", it).add() }
            batch.execute()
        }
    }

    override fun getLastSyncEpochMs(): Long =
        jdbi.withHandle<Long, Exception> { handle ->
            handle
                .createQuery("SELECT state_value FROM sync_state WHERE state_key = :key")
                .bind("key", LAST_SYNC_STATE_KEY)
                .mapTo(Long::class.java)
                .findOne()
                .orElse(0L)
        }

    override fun setLastSyncEpochMs(value: Long) {
        jdbi.useHandle<Exception> { handle ->
            val updated =
                handle
                    .createUpdate(
                        "UPDATE sync_state SET state_value = :value WHERE state_key = :key"
                    )
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            if (updated == 0) {
                handle
                    .createUpdate(
                        "INSERT INTO sync_state (state_key, state_value) VALUES (:key, :value)"
                    )
                    .bind("key", LAST_SYNC_STATE_KEY)
                    .bind("value", value)
                    .execute()
            }
        }
    }

    override fun seedStarterContacts() {
        val count =
            jdbi.withHandle<Long, Exception> { handle ->
                handle
                    .createQuery("SELECT COUNT(*) FROM contacts WHERE deleted = false")
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
                .createUpdate(
                    "UPDATE contacts SET deleted = true, version = version + 1 WHERE sync_id = :syncId"
                )
                .bind("syncId", syncId)
                .execute()
        }
    }

    override fun restore(syncId: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE contacts SET deleted = false, version = version + 1 WHERE sync_id = :syncId"
                )
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
                        UPDATE contacts
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
                insertCollections(
                    handle,
                    contactId,
                    contact.emails,
                    contact.phones,
                    contact.socialMedia,
                )
            }
        }
        return requireNotNull(findBySyncId(contact.syncId))
    }

    override fun markConflict(syncId: String, serverVersion: SyncContact) {
        val json = Jackson.asFormatString(serverVersion)
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE contacts SET sync_conflict = :json WHERE sync_id = :syncId")
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
                    UPDATE contacts
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
                        INSERT INTO contacts
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
        handle
            .createUpdate("DELETE FROM contact_emails WHERE contact_id = :id")
            .bind("id", contactId)
            .execute()
        if (emails.isNotEmpty()) {
            val batch =
                handle.prepareBatch(
                    "INSERT INTO contact_emails (contact_id, email) VALUES (:contactId, :email)"
                )
            emails.forEach { batch.bind("contactId", contactId).bind("email", it).add() }
            batch.execute()
        }

        handle
            .createUpdate("DELETE FROM contact_phones WHERE contact_id = :id")
            .bind("id", contactId)
            .execute()
        if (phones.isNotEmpty()) {
            val batch =
                handle.prepareBatch(
                    "INSERT INTO contact_phones (contact_id, phone) VALUES (:contactId, :phone)"
                )
            phones.forEach { batch.bind("contactId", contactId).bind("phone", it).add() }
            batch.execute()
        }

        handle
            .createUpdate("DELETE FROM contact_socials WHERE contact_id = :id")
            .bind("id", contactId)
            .execute()
        if (socialMedia.isNotEmpty()) {
            val batch =
                handle.prepareBatch(
                    "INSERT INTO contact_socials (contact_id, social_media) VALUES (:contactId, :socialMedia)"
                )
            socialMedia.forEach { batch.bind("contactId", contactId).bind("socialMedia", it).add() }
            batch.execute()
        }
    }

    private fun getContactId(handle: Handle, syncId: String): Long? =
        handle
            .createQuery("SELECT id FROM contacts WHERE sync_id = :syncId")
            .bind("syncId", syncId)
            .mapTo(Long::class.java)
            .findOne()
            .orElse(null)

    private data class FilterClause(
        val sql: String,
        val binder: (org.jdbi.v3.core.statement.Query) -> Unit,
    )

    private fun buildFilterClause(query: String?, includeDeleted: Boolean): FilterClause {
        val conditions = mutableListOf<String>()
        val bindings = mutableMapOf<String, Any>()

        conditions.add(if (includeDeleted) "deleted = true" else "deleted = false")

        if (!query.isNullOrBlank()) {
            conditions.add(
                "(LOWER(name) LIKE :likeQuery ESCAPE '!' OR LOWER(company) LIKE :likeQuery ESCAPE '!')"
            )
            bindings["likeQuery"] = "%${query.lowercase().escapeLike()}%"
        }

        val whereClause = conditions.joinToString(" AND ")
        return FilterClause(whereClause) { q ->
            bindings.forEach { (key, value) -> q.bind(key, value) }
        }
    }

    private fun mapContact(rs: java.sql.ResultSet, handle: Handle, contactId: Long): StoredContact {
        val emails =
            handle
                .createQuery("SELECT email FROM contact_emails WHERE contact_id = :id")
                .bind("id", contactId)
                .mapTo(String::class.java)
                .list()
        val phones =
            handle
                .createQuery("SELECT phone FROM contact_phones WHERE contact_id = :id")
                .bind("id", contactId)
                .mapTo(String::class.java)
                .list()
        val socials =
            handle
                .createQuery("SELECT social_media FROM contact_socials WHERE contact_id = :id")
                .bind("id", contactId)
                .mapTo(String::class.java)
                .list()

        return StoredContact(
            syncId = rs.getString("sync_id") ?: "unknown",
            name = rs.getString("name") ?: "unknown",
            emails = emails,
            phones = phones,
            socialMedia = socials,
            company = rs.getString("company") ?: "",
            companyAddress = rs.getString("company_address") ?: "",
            department = rs.getString("department") ?: "",
            updatedAtEpochMs = rs.getLong("updated_at_epoch_ms"),
            dirty = rs.getBoolean("dirty"),
            deleted = rs.getBoolean("deleted"),
            version = rs.getLong("version"),
            syncConflict = rs.getString("sync_conflict"),
        )
    }

    companion object {
        private const val LAST_SYNC_STATE_KEY = "last_contact_sync_epoch_ms"
    }
}

private fun String.escapeLike(): String = replace("!", "!!").replace("%", "!%").replace("_", "!_")
