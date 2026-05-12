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
                    INSERT INTO plt_outbox (id, payload_type, payload, status)
                    VALUES (:id, :payloadType, :payload, '${OutboxStatus.PENDING.name}')
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
                    SELECT id, payload_type, payload, status, created_at FROM plt_outbox
                    WHERE status = '${OutboxStatus.PENDING.name}'
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
                        status = OutboxStatus.valueOf(rs.getString("status")),
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
                    UPDATE plt_outbox SET status = '${OutboxStatus.PROCESSED.name}', processed_at = :processedAt
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
                    "UPDATE plt_outbox SET status = '${OutboxStatus.FAILED.name}', last_error = :error WHERE id = :id"
                )
                .bind("id", id)
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
                    WHERE status = '${OutboxStatus.FAILED.name}'
                    ORDER BY created_at DESC
                    """
                )
                .map { rs, _ ->
                    OutboxEntry(
                        id = rs.getObject("id", UUID::class.java),
                        payloadType = rs.getString("payload_type"),
                        payload = rs.getString("payload"),
                        status = OutboxStatus.valueOf(rs.getString("status")),
                        createdAt = rs.getTimestamp("created_at").toInstant(),
                    )
                }
                .list()
        }
    }
}
