package io.github.rygel.outerstellar.platform.persistence

import io.github.rygel.outerstellar.platform.model.AuditEntry

interface AuditRepository {
    fun log(entry: AuditEntry)

    fun findRecent(limit: Int = 50): List<AuditEntry>

    fun findPage(limit: Int, offset: Int): List<AuditEntry>

    fun countAll(): Long
}
