package dev.outerstellar.starter.service

import dev.outerstellar.starter.persistence.OutboxRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

interface SyncProvider {
    fun sync(): SyncStats
}

data class SyncStats(
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictCount: Int
)

class OutboxProcessor(
    private val outboxRepository: OutboxRepository,
    private val syncProvider: SyncProvider? = null,
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
                    when (entry.payloadType) {
                        "MESSAGE_CREATED", "MESSAGE_UPDATED", "MESSAGE_DELETED" -> {
                            logger.info("Outbox entry {} triggered sync check", entry.id)
                            syncProvider?.sync()
                        }
                        else -> logger.info("Processed outbox entry: {} (Type: {})", entry.id, entry.payloadType)
                    }
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
