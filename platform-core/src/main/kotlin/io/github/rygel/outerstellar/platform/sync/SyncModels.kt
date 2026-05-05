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
        fun validate(msg: SyncMessage): SyncMessage {
            val errors = mutableListOf<String>()
            if (msg.syncId.isBlank()) errors += "syncId must not be blank"
            if (msg.author.isBlank()) errors += "author must not be blank"
            if (msg.content.isBlank()) errors += "content must not be blank"
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
        fun validate(contact: SyncContact): SyncContact {
            val errors = mutableListOf<String>()
            if (contact.syncId.isBlank()) errors += "syncId must not be blank"
            if (contact.name.isBlank()) errors += "name must not be blank"
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
