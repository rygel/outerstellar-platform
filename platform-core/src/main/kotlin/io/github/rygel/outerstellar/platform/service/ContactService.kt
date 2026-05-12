package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.model.AuditEntry
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.StoredContact
import io.github.rygel.outerstellar.platform.model.ValidationException
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

    companion object {
        private const val MAX_PAGE_LIMIT = 1000
        const val MAX_NAME_LENGTH = 200
        const val MAX_COMPANY_LENGTH = 200
        const val MAX_ADDRESS_LENGTH = 500
        const val MAX_EMAIL_LENGTH = 255
        const val MAX_PHONE_LENGTH = 50
        const val MAX_SOCIAL_MEDIA_LENGTH = 255
    }

    fun listContacts(query: String? = null, limit: Int = 100, offset: Int = 0): List<ContactSummary> {
        val limit = limit.coerceIn(1, MAX_PAGE_LIMIT)
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
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Name is required."
        if (name.length > MAX_NAME_LENGTH) errors += "Name cannot exceed $MAX_NAME_LENGTH characters."
        if (company.length > MAX_COMPANY_LENGTH) errors += "Company cannot exceed $MAX_COMPANY_LENGTH characters."
        if (companyAddress.length > MAX_ADDRESS_LENGTH)
            errors += "Address cannot exceed $MAX_ADDRESS_LENGTH characters."
        if (department.length > MAX_COMPANY_LENGTH) errors += "Department cannot exceed $MAX_COMPANY_LENGTH characters."
        emails.forEachIndexed { i, email ->
            if (email.length > MAX_EMAIL_LENGTH) errors += "Email ${i + 1} cannot exceed $MAX_EMAIL_LENGTH characters."
        }
        phones.forEachIndexed { i, phone ->
            if (phone.length > MAX_PHONE_LENGTH) errors += "Phone ${i + 1} cannot exceed $MAX_PHONE_LENGTH characters."
        }
        socialMedia.forEachIndexed { i, sm ->
            if (sm.length > MAX_SOCIAL_MEDIA_LENGTH)
                errors += "Social media ${i + 1} cannot exceed $MAX_SOCIAL_MEDIA_LENGTH characters."
        }
        if (errors.isNotEmpty()) throw ValidationException(errors)

        logger.info("Creating contact name={}", name)
        val contact =
            repository.createLocalContact(name, emails, phones, socialMedia, company, companyAddress, department)
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
        val errors = mutableListOf<String>()
        if (contact.name.isBlank()) errors += "Name is required."
        if (contact.name.length > MAX_NAME_LENGTH) errors += "Name cannot exceed $MAX_NAME_LENGTH characters."
        if (contact.company.length > MAX_COMPANY_LENGTH)
            errors += "Company cannot exceed $MAX_COMPANY_LENGTH characters."
        if (contact.companyAddress.length > MAX_ADDRESS_LENGTH)
            errors += "Address cannot exceed $MAX_ADDRESS_LENGTH characters."
        if (contact.department.length > MAX_COMPANY_LENGTH)
            errors += "Department cannot exceed $MAX_COMPANY_LENGTH characters."
        contact.emails.forEachIndexed { i, email ->
            if (email.length > MAX_EMAIL_LENGTH) errors += "Email ${i + 1} cannot exceed $MAX_EMAIL_LENGTH characters."
        }
        contact.phones.forEachIndexed { i, phone ->
            if (phone.length > MAX_PHONE_LENGTH) errors += "Phone ${i + 1} cannot exceed $MAX_PHONE_LENGTH characters."
        }
        contact.socialMedia.forEachIndexed { i, sm ->
            if (sm.length > MAX_SOCIAL_MEDIA_LENGTH)
                errors += "Social media ${i + 1} cannot exceed $MAX_SOCIAL_MEDIA_LENGTH characters."
        }
        if (errors.isNotEmpty()) throw ValidationException(errors)

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

    fun getChangesSince(updatedAtEpochMs: Long): io.github.rygel.outerstellar.platform.sync.SyncPullContactResponse {
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
        val conflicts = mutableListOf<io.github.rygel.outerstellar.platform.sync.SyncContactConflict>()

        val process = {
            request.contacts.forEach { pushedContact ->
                val existing = repository.findBySyncId(pushedContact.syncId)
                if (existing != null && existing.updatedAtEpochMs > pushedContact.updatedAtEpochMs) {
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
