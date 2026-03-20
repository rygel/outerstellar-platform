package io.github.rygel.outerstellar.platform.sync

import io.konform.validation.Validation
import io.konform.validation.constraints.minLength

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncMessage(
    val syncId: String,
    val author: String,
    val content: String,
    val updatedAtEpochMs: Long,
    val deleted: Boolean = false,
) {
    companion object {
        val validate =
            Validation<SyncMessage> {
                SyncMessage::syncId { minLength(1) }
                SyncMessage::author { minLength(1) }
                SyncMessage::content { minLength(1) }
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushRequest(val messages: List<SyncMessage> = emptyList()) {
    companion object {
        val validate =
            Validation<SyncPushRequest> {
                SyncPushRequest::messages onEach { run(SyncMessage.validate) }
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncConflict(
    val syncId: String,
    val reason: String,
    val serverMessage: SyncMessage? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushResponse(
    val appliedCount: Int = 0,
    val conflicts: List<SyncConflict> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPullResponse(
    val messages: List<SyncMessage> = emptyList(),
    val serverTimestamp: Long = 0,
)

data class SyncStats(val pushedCount: Int = 0, val pulledCount: Int = 0, val conflictCount: Int = 0)

@JsonIgnoreProperties(ignoreUnknown = true)
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
        val validate =
            Validation<SyncContact> {
                SyncContact::syncId { minLength(1) }
                SyncContact::name { minLength(1) }
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushContactRequest(val contacts: List<SyncContact> = emptyList()) {
    companion object {
        val validate =
            Validation<SyncPushContactRequest> {
                SyncPushContactRequest::contacts onEach { run(SyncContact.validate) }
            }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncContactConflict(
    val syncId: String,
    val reason: String,
    val serverContact: SyncContact? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPushContactResponse(
    val appliedCount: Int = 0,
    val conflicts: List<SyncContactConflict> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SyncPullContactResponse(
    val contacts: List<SyncContact> = emptyList(),
    val serverTimestamp: Long = 0,
)

/**
 * Annotation to ignore unknown properties during JSON deserialization. Replaces Jackson's
 * JsonIgnoreProperties to avoid dependency conflicts with http4k 6.x.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class JsonIgnoreProperties(val ignoreUnknown: Boolean = false)
