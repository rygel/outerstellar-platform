package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.persistence.OutboxEntry
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory

class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val transactionManager: TransactionManager? = null,
    private val maxBatchSize: Int = 10,
) {
    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)

    private val consecutiveEmptyPolls = AtomicInteger(0)
    private val totalProcessed = AtomicLong(0)
    private val totalFailed = AtomicLong(0)

    val stats: OutboxStats
        get() =
            OutboxStats(
                totalProcessed = totalProcessed.get(),
                totalFailed = totalFailed.get(),
                consecutiveEmptyPolls = consecutiveEmptyPolls.get(),
            )

    /**
     * Returns the recommended delay in ms before the next poll. Increases exponentially (1s, 2s, 4s, ... up to 30s)
     * when empty.
     */
    val backoffMs: Long
        get() {
            val empty = consecutiveEmptyPolls.get()
            if (empty == 0) return 0L
            val delay = BACKOFF_BASE_MS shl (empty.coerceAtMost(MAX_BACKOFF_SHIFT) - 1)

    companion object {
        private const val BACKOFF_BASE_MS = 1000L
        private const val MAX_BACKOFF_SHIFT = 5
        private const val MAX_BACKOFF_MS = 30_000L
    }

    fun processPending(): Boolean {
        var batchSize: Int
        do {
            val entries = outboxRepository.listPending(maxBatchSize)
            if (entries.isEmpty()) {
                consecutiveEmptyPolls.incrementAndGet()
                return false
            }
            consecutiveEmptyPolls.set(0)
            batchSize = entries.size
            logger.debug("Processing {} outbox entries", batchSize)
            processEntries(entries)
        } while (batchSize == maxBatchSize)
        return true
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processEntries(entries: List<OutboxEntry>) {
        entries.forEach { entry ->
            try {
                logger.debug("Processing entry: {} ({})", entry.id, entry.payloadType)

                if (transactionManager != null) {
                    transactionManager.inTransaction { outboxRepository.markProcessed(entry.id) }
                } else {
                    outboxRepository.markProcessed(entry.id)
                }
                totalProcessed.incrementAndGet()
            } catch (e: Exception) {
                logger.error("Error processing outbox entry {}: {}", entry.id, e.message, e)
                outboxRepository.markFailed(entry.id, e.message ?: "Unknown error")
                totalFailed.incrementAndGet()
            }
        }
    }
}

data class OutboxStats(val totalProcessed: Long, val totalFailed: Long, val consecutiveEmptyPolls: Int)
