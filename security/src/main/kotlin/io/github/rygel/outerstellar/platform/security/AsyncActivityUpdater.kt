package io.github.rygel.outerstellar.platform.security

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.slf4j.LoggerFactory

/**
 * Buffers `updateLastActivity` calls and flushes them in a single batch on a schedule, removing this write from the
 * per-request hot path.
 *
 * Each unique user is deduplicated — 100 requests from the same user before the next flush produce exactly one DB
 * write. Flush is driven externally (e.g. by the outbox scheduler) so no additional threads are created.
 *
 * Usage:
 * - Call [record] in request filters instead of `userRepository.updateLastActivity`.
 * - Call [flush] from a scheduled job (every 30 s is sufficient).
 * - Call [flush] once more in the shutdown hook for a final drain.
 */
class AsyncActivityUpdater(private val userRepository: UserRepository) {

    private val logger = LoggerFactory.getLogger(AsyncActivityUpdater::class.java)
    private val pending = ConcurrentHashMap<UUID, Unit>()

    init {
        Runtime.getRuntime().addShutdownHook(Thread({ flush() }, "activity-flush-shutdown"))
    }

    /** Record that [userId] was active. Safe to call from any thread; never blocks. */
    fun record(userId: UUID) {
        pending[userId] = Unit
    }

    /**
     * Drain all pending records and write them to the database. Intended to be called on a fixed-delay schedule, not
     * from the request path.
     */
    fun flush() {
        if (pending.isEmpty()) return
        val batch = pending.keys().toList()
        var written = 0
        batch.forEach { id ->
            pending.remove(id)
            try {
                userRepository.updateLastActivity(id)
                written++
            } catch (e: Exception) {
                logger.warn("Failed to flush activity update for user {}: {}", id, e.message)
            }
        }
        if (written > 0) logger.debug("Flushed last-activity for {} user(s)", written)
    }
}
