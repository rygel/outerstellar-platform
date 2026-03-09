package dev.outerstellar.starter.service

import dev.outerstellar.starter.persistence.OutboxRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val intervalMs: Long = 5000
) {
    private val logger = LoggerFactory.getLogger(OutboxProcessor::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor()

    fun start() {
        executor.scheduleAtFixedRate(::processEntries, intervalMs, intervalMs, TimeUnit.MILLISECONDS)
        logger.info("Outbox processor started with interval {}ms", intervalMs)
    }

    fun stop() {
        executor.shutdown()
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
        logger.info("Outbox processor stopped")
    }

    private fun processEntries() {
        try {
            val entries = outboxRepository.fetchUnprocessed()
            if (entries.isEmpty()) return

            logger.debug("Processing {} outbox entries", entries.size)
            entries.forEach { entry ->
                try {
                    // Simulate processing (e.g., sending a notification, sync event, etc.)
                    logger.info("Processed outbox entry: {} (Type: {})", entry.id, entry.payloadType)
                    outboxRepository.markProcessed(entry.id)
                } catch (e: Exception) {
                    logger.error("Failed to process outbox entry {}", entry.id, e)
                    outboxRepository.markFailed(entry.id, e.message ?: "Unknown error")
                }
            }
        } catch (e: Exception) {
            logger.error("Error fetching outbox entries", e)
        }
    }
}
