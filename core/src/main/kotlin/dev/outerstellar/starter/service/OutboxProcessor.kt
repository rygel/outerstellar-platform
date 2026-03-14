package dev.outerstellar.starter.service

import dev.outerstellar.starter.persistence.OutboxEntry
import dev.outerstellar.starter.persistence.OutboxRepository
import dev.outerstellar.starter.persistence.TransactionManager
import org.slf4j.LoggerFactory

private const val MAX_BATCH_SIZE = 10

class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val transactionManager: TransactionManager? = null,
) {
    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)

    fun processPending() {
        val entries = outboxRepository.listPending(MAX_BATCH_SIZE)
        if (entries.isEmpty()) return

        logger.info("Processing {} outbox entries", entries.size)
        processEntries(entries)
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
