package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.StoredContact
import io.github.rygel.outerstellar.platform.persistence.AuditRepository
import io.github.rygel.outerstellar.platform.persistence.ContactRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import org.slf4j.LoggerFactory

class ContactService(
    private val repository: ContactRepository,
    private val eventPublisher: EventPublisher = NoOpEventPublisher,
    private val transactionManager: TransactionManager? = null,
    private val auditRepository: AuditRepository? = null,
) {
    private val logger = LoggerFactory.getLogger(ContactService::class.java)

    fun listContacts(
        query: String? = null,
        limit: Int = 100,
        offset: Int = 0,
    ): List<ContactSummary> {
        logger.debug("Listing contacts query='{}' limit={} offset={}", query, limit, offset)
        return repository.listContacts(query, limit, offset)
    }

    fun countContacts(query: String? = null): Long {
        return repository.countContacts(query)
    }

    fun getContactBySyncId(syncId: String): StoredContact? {
        return repository.findBySyncId(syncId)
    }

    @Suppress("LongParameterList")
    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): StoredContact {
        logger.info("Creating contact name={}", name)
        val contact =
            repository.createLocalContact(
                name,
                emails,
                phones,
                socialMedia,
                company,
                companyAddress,
                department,
            )
        auditRepository?.log(
            AuditEntry(
                actorId = null,
                actorUsername = null,
                targetId = contact.syncId,
                targetUsername = contact.name,
                action = "CONTACT_CREATED",
                detail = null,
            )
        )
        eventPublisher.publishRefresh("contact-list-panel")
        return contact
    }

    fun updateContact(contact: StoredContact): StoredContact {
        val updated = contact.copy(dirty = true)
        val result = repository.updateContact(updated)
        auditRepository?.log(
            AuditEntry(
                actorId = null,
                actorUsername = null,
                targetId = result.syncId,
                targetUsername = result.name,
                action = "CONTACT_UPDATED",
                detail = null,
            )
        )
        eventPublisher.publishRefresh("contact-list-panel")
        return result
    }

    fun deleteContact(syncId: String) {
        logger.info("Soft deleting contact syncId={}", syncId)
        repository.softDelete(syncId)
        auditRepository?.log(
            AuditEntry(
                actorId = null,
                actorUsername = null,
                targetId = syncId,
                targetUsername = null,
                action = "CONTACT_DELETED",
                detail = null,
            )
        )
        eventPublisher.publishRefresh("contact-list-panel")
    }

    fun getChangesSince(
        updatedAtEpochMs: Long
    ): io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse {
        val changes = repository.findChangesSince(updatedAtEpochMs)
        val serverTimestamp = System.currentTimeMillis()
        return io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse(
            contacts = changes.map { it.toSyncContact() },
            serverTimestamp = serverTimestamp,
        )
    }

    fun processPushRequest(
        request: io.github.rygel.outerstellar.platform.sync.SyncPushContactRequest
    ): io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse {
        var applied = 0
        val conflicts =
            mutableListOf<io.github.rygel.outerstellar.platform.sync.SyncContactConflict>()

        val process = {
            request.contacts.forEach { pushedContact ->
                val existing = repository.findBySyncId(pushedContact.syncId)
                if (
                    existing != null && existing.updatedAtEpochMs > pushedContact.updatedAtEpochMs
                ) {
                    conflicts.add(
                        io.github.rygel.outerstellar.platform.sync.SyncContactConflict(
                            syncId = pushedContact.syncId,
                            reason = "Server has newer version",
                            serverContact = existing.toSyncContact(),
                        )
                    )
                } else {
                    repository.upsertSyncedContact(pushedContact, false)
                    applied++
                }
            }
        }

        transactionManager?.inTransaction(process) ?: process()

        if (applied > 0 || conflicts.isNotEmpty()) {
            eventPublisher.publishRefresh("contact-list-panel")
        }

        return io.github.rygel.outerstellar.platform.sync.SyncPushContactResponse(
            appliedCount = applied,
            conflicts = conflicts,
        )
    }
}
