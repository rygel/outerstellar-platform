package dev.outerstellar.starter.persistence

import java.time.Instant
import java.util.*

data class OutboxEntry(
    val id: UUID,
    val payloadType: String,
    val payload: String,
    val createdAt: Instant = Instant.now(),
    val processedAt: Instant? = null,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val status: String = "PENDING"
)

interface OutboxRepository {
    fun save(entry: OutboxEntry)
    fun fetchUnprocessed(limit: Int = 10): List<OutboxEntry>
    fun markProcessed(id: UUID)
    fun markFailed(id: UUID, error: String, maxRetries: Int = 5)
    fun softDelete(id: UUID)
    fun countByStatus(status: String): Int
}

interface TransactionManager {
    fun <T> inTransaction(block: () -> T): T
}
