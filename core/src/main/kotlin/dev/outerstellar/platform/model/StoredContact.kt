package dev.outerstellar.platform.model

import dev.outerstellar.platform.sync.SyncContact

data class StoredContact(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val socialMedia: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String,
    val updatedAtEpochMs: Long,
    val dirty: Boolean,
    val deleted: Boolean,
    val version: Long = 1,
    val syncConflict: String? = null,
) {
    fun toSummary(): ContactSummary =
        ContactSummary(
            syncId = syncId,
            name = name,
            emails = emails,
            phones = phones,
            socialMedia = socialMedia,
            company = company,
            companyAddress = companyAddress,
            department = department,
            updatedAtEpochMs = updatedAtEpochMs,
            dirty = dirty,
            version = version,
            hasConflict = syncConflict != null,
        )

    fun toSyncContact(): SyncContact =
        SyncContact(
            syncId = syncId,
            name = name,
            emails = emails,
            phones = phones,
            socialMedia = socialMedia,
            company = company,
            companyAddress = companyAddress,
            department = department,
            updatedAtEpochMs = updatedAtEpochMs,
            deleted = deleted,
        )
}
