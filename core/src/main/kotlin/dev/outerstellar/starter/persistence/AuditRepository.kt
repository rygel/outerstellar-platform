package dev.outerstellar.starter.persistence

import dev.outerstellar.starter.model.AuditEntry

interface AuditRepository {
    fun log(entry: AuditEntry)

    fun findRecent(limit: Int = 50): List<AuditEntry>
}
