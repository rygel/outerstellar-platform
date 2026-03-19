package dev.outerstellar.platform.persistence

import dev.outerstellar.platform.model.AuditEntry
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiAuditRepository(private val jdbi: Jdbi) : AuditRepository {

    override fun log(entry: AuditEntry) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO audit_log (actor_id, actor_username, target_id, target_username, action, detail)
                    VALUES (:actorId, :actorUsername, :targetId, :targetUsername, :action, :detail)
                    """
                )
                .bind("actorId", entry.actorId?.let { UUID.fromString(it) })
                .bind("actorUsername", entry.actorUsername)
                .bind("targetId", entry.targetId?.let { UUID.fromString(it) })
                .bind("targetUsername", entry.targetUsername)
                .bind("action", entry.action)
                .bind("detail", entry.detail)
                .execute()
        }
    }

    override fun findRecent(limit: Int): List<AuditEntry> {
        return jdbi.withHandle<List<AuditEntry>, Exception> { handle ->
            handle
                .createQuery("SELECT * FROM audit_log ORDER BY created_at DESC LIMIT :limit")
                .bind("limit", limit)
                .map { rs, _ ->
                    AuditEntry(
                        id = rs.getLong("id"),
                        actorId = rs.getObject("actor_id")?.toString(),
                        actorUsername = rs.getString("actor_username"),
                        targetId = rs.getObject("target_id")?.toString(),
                        targetUsername = rs.getString("target_username"),
                        action = rs.getString("action"),
                        detail = rs.getString("detail"),
                        createdAt =
                            rs.getTimestamp("created_at")?.toInstant() ?: java.time.Instant.now(),
                    )
                }
                .list()
        }
    }
}
