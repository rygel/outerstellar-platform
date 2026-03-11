package dev.outerstellar.starter.model

data class ContactSummary(
    val syncId: String,
    val name: String,
    val emails: List<String>,
    val phones: List<String>,
    val company: String,
    val companyAddress: String,
    val department: String,
    val updatedAtEpochMs: Long,
    val dirty: Boolean,
    val version: Long = 1,
    val hasConflict: Boolean = false,
)
