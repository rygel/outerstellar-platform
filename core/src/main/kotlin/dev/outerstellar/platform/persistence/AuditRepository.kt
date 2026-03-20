package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.AuditEntry

interface AuditRepository {
    fun log(entry: AuditEntry)

    fun findRecent(limit: Int = 50): List<AuditEntry>

    fun findPage(limit: Int, offset: Int): List<AuditEntry>

    fun countAll(): Long
}
