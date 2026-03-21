package io.github.rygel.outerstellar.platform.persistence

import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import org.jdbi.v3.core.Jdbi

class JdbiOutboxRepository(private val jdbi: Jdbi) : OutboxRepository {

    override fun save(entry: OutboxEntry) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO outbox (id, payload_type, payload, status)
                    VALUES (:id, :payloadType, :payload, 'PENDING')
                    """
                )
                .bind("id", entry.id)
                .bind("payloadType", entry.payloadType)
                .bind("payload", entry.payload)
                .execute()
        }
    }

    override fun listPending(limit: Int): List<OutboxEntry> {
        return jdbi.withHandle<List<OutboxEntry>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT * FROM outbox
                    WHERE status = 'PENDING'
                    ORDER BY created_at ASC
                    LIMIT :limit
                    """
                )
                .bind("limit", limit)
                .map { rs, _ ->
                    OutboxEntry(
                        id = rs.getObject("id", UUID::class.java),
                        payloadType = rs.getString("payload_type"),
                        payload = rs.getString("payload"),
                        status = rs.getString("status"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                    )
                }
                .list()
        }
    }

    override fun markProcessed(id: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    UPDATE outbox SET status = 'PROCESSED', processed_at = :processedAt
                    WHERE id = :id
                    """
                )
                .bind("id", id)
                .bind("processedAt", LocalDateTime.now(ZoneOffset.UTC))
                .execute()
        }
    }

    override fun markFailed(id: UUID, error: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE outbox SET status = 'FAILED', last_error = :error WHERE id = :id"
                )
                .bind("id", id)
                .bind("error", error)
                .execute()
        }
    }

    override fun getStats(): Map<String, Int> {
        return jdbi.withHandle<Map<String, Int>, Exception> { handle ->
            handle
                .createQuery("SELECT status, COUNT(*) AS cnt FROM outbox GROUP BY status")
                .map { rs, _ -> rs.getString("status") to rs.getInt("cnt") }
                .list()
                .toMap()
        }
    }

    override fun listFailed(): List<OutboxEntry> {
        return jdbi.withHandle<List<OutboxEntry>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT * FROM outbox
                    WHERE status = 'FAILED'
                    ORDER BY created_at DESC
                    """
                )
                .map { rs, _ ->
                    OutboxEntry(
                        id = rs.getObject("id", UUID::class.java),
                        payloadType = rs.getString("payload_type"),
                        payload = rs.getString("payload"),
                        status = rs.getString("status"),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                    )
                }
                .list()
        }
    }
}
