package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.util.UUID

data class OutboxEntry(
    val id: UUID,
    val payloadType: String,
    val payload: String,
    val status: String,
    val createdAt: Instant = Instant.now(),
)

interface OutboxRepository {
    fun save(entry: OutboxEntry)

    fun listPending(limit: Int): List<OutboxEntry>

    fun markProcessed(id: UUID)

    fun markFailed(id: UUID, error: String)

    fun getStats(): Map<String, Int>

    fun listFailed(): List<OutboxEntry>
}

interface TransactionManager {
    fun <T> inTransaction(block: () -> T): T
}
