package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.util.*
import org.jdbi.v3.core.Jdbi

class JdbiOutboxRepository(private val jdbi: Jdbi) : OutboxRepository {

    override fun save(entry: OutboxEntry) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO plt_outbox (id, payload_type, payload, status)
                    VALUES (:id, :payloadType, :payload, :status)
                    """
                )
                .bind("id", entry.id)
                .bind("payloadType", entry.payloadType)
                .bind("payload", entry.payload)
                .bind("status", OutboxStatus.PENDING.name)
                .execute()
        }
    }

    override fun listPending(limit: Int): List<OutboxEntry> {
        return jdbi.withHandle<List<OutboxEntry>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT id, payload_type, payload, status, created_at FROM plt_outbox
                    WHERE status = :status
                    ORDER BY created_at ASC
                    LIMIT :limit
                    """
                )
                .bind("status", OutboxStatus.PENDING.name)
                .bind("limit", limit)
                .map { rs, _ ->
                    OutboxEntry(
                        id = rs.getObject("id", UUID::class.java),
                        payloadType = rs.getString("payload_type"),
                        payload = rs.getString("payload"),
                        status = OutboxStatus.valueOf(rs.getString("status")),
                        createdAt = rs.getRequiredInstant("created_at"),
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
                    UPDATE plt_outbox SET status = :status, processed_at = :processedAt
                    WHERE id = :id
                    """
                )
                .bind("id", id)
                .bind("status", OutboxStatus.PROCESSED.name)
                .bind("processedAt", Instant.now())
                .execute()
        }
    }

    override fun markFailed(id: UUID, error: String) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("UPDATE plt_outbox SET status = :status, last_error = :error WHERE id = :id")
                .bind("id", id)
                .bind("status", OutboxStatus.FAILED.name)
                .bind("error", error)
                .execute()
        }
    }

    override fun getStats(): Map<OutboxStatus, Int> {
        return jdbi.withHandle<Map<OutboxStatus, Int>, Exception> { handle ->
            handle
                .createQuery("SELECT status, COUNT(*) AS cnt FROM plt_outbox GROUP BY status")
                .map { rs, _ -> OutboxStatus.valueOf(rs.getString("status")) to rs.getInt("cnt") }
                .list()
                .toMap()
        }
    }

    override fun listFailed(): List<OutboxEntry> {
        return jdbi.withHandle<List<OutboxEntry>, Exception> { handle ->
            handle
                .createQuery(
                    """
                    SELECT id, payload_type, payload, status, created_at FROM plt_outbox
                    WHERE status = :status
                    ORDER BY created_at DESC
                    """
                )
                .bind("status", OutboxStatus.FAILED.name)
                .map { rs, _ ->
                    OutboxEntry(
                        id = rs.getObject("id", UUID::class.java),
                        payloadType = rs.getString("payload_type"),
                        payload = rs.getString("payload"),
                        status = OutboxStatus.valueOf(rs.getString("status")),
                        createdAt = rs.getRequiredInstant("created_at"),
                    )
                }
                .list()
        }
    }
}
