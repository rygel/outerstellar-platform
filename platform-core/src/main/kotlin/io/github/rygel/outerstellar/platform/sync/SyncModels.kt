package io.github.rygel.outerstellar.platform.sync

import io.github.rygel.outerstellar.platform.model.ValidationException
import kotlinx.serialization.Serializable

@Serializable
data class SyncMessage(
    val syncId: String,
    val author: String,
    val content: String,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false,
) {
    companion object {
        const val MAX_AUTHOR_LENGTH = 100
        const val MAX_CONTENT_LENGTH = 500

        fun validate(msg: SyncMessage): SyncMessage {
            val errors = mutableListOf<String>()
            if (msg.syncId.isBlank()) errors += "syncId must not be blank"
            if (msg.author.isBlank()) errors += "author must not be blank"
            if (msg.content.isBlank()) errors += "content must not be blank"
            if (msg.author.length > MAX_AUTHOR_LENGTH) errors += "author cannot exceed $MAX_AUTHOR_LENGTH characters"
            if (msg.content.length > MAX_CONTENT_LENGTH)
                errors += "content cannot exceed $MAX_CONTENT_LENGTH characters"
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return msg
        }
    }
}

@Serializable
data class SyncPushRequest(val messages: List<SyncMessage> = emptyList()) {
    companion object {
        fun validate(request: SyncPushRequest): SyncPushRequest {
            request.messages.forEach { SyncMessage.validate(it) }
            return request
        }
    }
}

@Serializable data class SyncConflict(val syncId: String, val reason: String, val serverMessage: SyncMessage? = null)

@Serializable data class SyncPushResponse(val appliedCount: Int = 0, val conflicts: List<SyncConflict> = emptyList())

@Serializable data class SyncPullResponse(val messages: List<SyncMessage> = emptyList(), val serverTimestamp: Long = 0)

data class SyncStats(val pushedCount: Int = 0, val pulledCount: Int = 0, val conflictCount: Int = 0)

@Serializable
data class SyncContact(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val socialMedia: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false,
) {
    companion object {
        const val MAX_NAME_LENGTH = 200
        const val MAX_COMPANY_LENGTH = 200
        const val MAX_ADDRESS_LENGTH = 500
        const val MAX_EMAIL_LENGTH = 255
        const val MAX_PHONE_LENGTH = 50
        const val MAX_SOCIAL_MEDIA_LENGTH = 255

        fun validate(contact: SyncContact): SyncContact {
            val errors = mutableListOf<String>()
            if (contact.syncId.isBlank()) errors += "syncId must not be blank"
            if (contact.name.isBlank()) errors += "name must not be blank"
            if (contact.name.length > MAX_NAME_LENGTH) errors += "name cannot exceed $MAX_NAME_LENGTH characters"
            if (contact.company.length > MAX_COMPANY_LENGTH)
                errors += "company cannot exceed $MAX_COMPANY_LENGTH characters"
            if (contact.companyAddress.length > MAX_ADDRESS_LENGTH)
                errors += "companyAddress cannot exceed $MAX_ADDRESS_LENGTH characters"
            if (contact.department.length > MAX_COMPANY_LENGTH)
                errors += "department cannot exceed $MAX_COMPANY_LENGTH characters"
            contact.emails.forEachIndexed { i, email ->
                if (email.length > MAX_EMAIL_LENGTH) errors += "email[$i] cannot exceed $MAX_EMAIL_LENGTH characters"
            }
            contact.phones.forEachIndexed { i, phone ->
                if (phone.length > MAX_PHONE_LENGTH) errors += "phone[$i] cannot exceed $MAX_PHONE_LENGTH characters"
            }
            contact.socialMedia.forEachIndexed { i, sm ->
                if (sm.length > MAX_SOCIAL_MEDIA_LENGTH)
                    errors += "socialMedia[$i] cannot exceed $MAX_SOCIAL_MEDIA_LENGTH characters"
            }
            if (errors.isNotEmpty()) throw ValidationException(errors)
            return contact
        }
    }
}

@Serializable
data class SyncPushContactRequest(val contacts: List<SyncContact> = emptyList()) {
    companion object {
        fun validate(request: SyncPushContactRequest): SyncPushContactRequest {
            request.contacts.forEach { SyncContact.validate(it) }
            return request
        }
    }
}

@Serializable
data class SyncContactConflict(val syncId: String, val reason: String, val serverContact: SyncContact? = null)

@Serializable
data class SyncPushContactResponse(val appliedCount: Int = 0, val conflicts: List<SyncContactConflict> = emptyList())

@Serializable
data class SyncPullContactResponse(val contacts: List<SyncContact> = emptyList(), val serverTimestamp: Long = 0)
