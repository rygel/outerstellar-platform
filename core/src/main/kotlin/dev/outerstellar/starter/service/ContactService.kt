package dev.outerstellar.starter.service

import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.StoredContact
import dev.outerstellar.starter.persistence.ContactRepository
import org.slf4j.LoggerFactory

class ContactService(
    private val repository: ContactRepository
) {
    private val logger = LoggerFactory.getLogger(ContactService::class.java)

    fun listContacts(query: String? = null, limit: Int = 100, offset: Int = 0): List<ContactSummary> {
        logger.debug("Listing contacts query='{}' limit={} offset={}", query, limit, offset)
        return repository.listContacts(query, limit, offset)
    }

    fun countContacts(query: String? = null): Long {
        return repository.countContacts(query)
    }

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        company: String,
        companyAddress: String,
        department: String
    ): StoredContact {
        logger.info("Creating contact name={}", name)
        return repository.createLocalContact(name, emails, phones, company, companyAddress, department)
    }

    fun updateContact(contact: StoredContact): StoredContact {
        val updated = contact.copy(dirty = true)
        return repository.updateContact(updated)
    }

    fun deleteContact(syncId: String) {
        logger.info("Soft deleting contact syncId={}", syncId)
        repository.softDelete(syncId)
    }

    fun getChangesSince(updatedAtEpochMs: Long): dev.outerstellar.starter.sync.SyncPullContactResponse {
        val changes = repository.findChangesSince(updatedAtEpochMs)
        val serverTimestamp = System.currentTimeMillis()
        return dev.outerstellar.starter.sync.SyncPullContactResponse(
            contacts = changes.map { it.toSyncContact() },
            serverTimestamp = serverTimestamp
        )
    }

    fun processPushRequest(request: dev.outerstellar.starter.sync.SyncPushContactRequest): dev.outerstellar.starter.sync.SyncPushContactResponse {
        var applied = 0
        val conflicts = mutableListOf<dev.outerstellar.starter.sync.SyncContactConflict>()

        request.contacts.forEach { pushedContact ->
            val existing = repository.findBySyncId(pushedContact.syncId)
            if (existing != null && existing.updatedAtEpochMs > pushedContact.updatedAtEpochMs) {
                conflicts.add(
                    dev.outerstellar.starter.sync.SyncContactConflict(
                        syncId = pushedContact.syncId,
                        reason = "Server has newer version",
                        serverContact = existing.toSyncContact()
                    )
                )
            } else {
                repository.upsertSyncedContact(pushedContact, false)
                applied++
            }
        }

        return dev.outerstellar.starter.sync.SyncPushContactResponse(
            appliedCount = applied,
            conflicts = conflicts
        )
    }
}
