package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.persistence.OutboxEntry
import io.github.rygel.outerstellar.platform.persistence.OutboxRepository
import io.github.rygel.outerstellar.platform.persistence.TransactionManager
import org.slf4j.LoggerFactory

private const val MAX_BATCH_SIZE = 10

class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val transactionManager: TransactionManager? = null,
) {
    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)

    fun processPending() {
        var batchSize: Int
        do {
            val entries = outboxRepository.listPending(MAX_BATCH_SIZE)
            if (entries.isEmpty()) return
            batchSize = entries.size
            logger.info("Processing {} outbox entries", batchSize)
            processEntries(entries)
        } while (batchSize == MAX_BATCH_SIZE)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processEntries(entries: List<OutboxEntry>) {
        entries.forEach { entry ->
            try {
                // Here we would normally sync to external systems
                logger.debug("Processing entry: {} ({})", entry.id, entry.payloadType)

                if (transactionManager != null) {
                    transactionManager.inTransaction { outboxRepository.markProcessed(entry.id) }
                } else {
                    outboxRepository.markProcessed(entry.id)
                }
            } catch (e: Exception) {
                logger.error("Error processing outbox entry {}: {}", entry.id, e.message, e)
                outboxRepository.markFailed(entry.id, e.message ?: "Unknown error")
            }
        }
    }
}
