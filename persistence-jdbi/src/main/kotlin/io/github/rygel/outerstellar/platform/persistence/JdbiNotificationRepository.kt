package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import org.jdbi.v3.core.Jdbi

class JdbiNotificationRepository(private val jdbi: Jdbi) : NotificationRepository {

    override fun save(notification: Notification) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    """
                    INSERT INTO notifications (id, user_id, title, body, type, created_at)
                    VALUES (:id, :userId, :title, :body, :type, :createdAt)
                    """
                )
                .bind("id", notification.id)
                .bind("userId", notification.userId)
                .bind("title", notification.title)
                .bind("body", notification.body)
                .bind("type", notification.type)
                .bind(
                    "createdAt",
                    notification.createdAt.atOffset(ZoneOffset.UTC).toLocalDateTime(),
                )
                .execute()
        }
    }

    override fun findByUserId(userId: UUID, limit: Int): List<Notification> {
        return jdbi.withHandle<List<Notification>, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT * FROM notifications WHERE user_id = :userId ORDER BY created_at DESC LIMIT :limit"
                )
                .bind("userId", userId)
                .bind("limit", limit)
                .map { rs, _ -> mapNotification(rs) }
                .list()
        }
    }

    override fun countUnread(userId: UUID): Int {
        return jdbi.withHandle<Int, Exception> { handle ->
            handle
                .createQuery(
                    "SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL"
                )
                .bind("userId", userId)
                .mapTo(Int::class.java)
                .one()
        }
    }

    override fun markRead(id: UUID, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE notifications SET read_at = :readAt WHERE id = :id AND user_id = :userId"
                )
                .bind("readAt", LocalDateTime.now(ZoneOffset.UTC))
                .bind("id", id)
                .bind("userId", userId)
                .execute()
        }
    }

    override fun markAllRead(userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate(
                    "UPDATE notifications SET read_at = :readAt WHERE user_id = :userId AND read_at IS NULL"
                )
                .bind("readAt", LocalDateTime.now(ZoneOffset.UTC))
                .bind("userId", userId)
                .execute()
        }
    }

    override fun delete(id: UUID, userId: UUID) {
        jdbi.useHandle<Exception> { handle ->
            handle
                .createUpdate("DELETE FROM notifications WHERE id = :id AND user_id = :userId")
                .bind("id", id)
                .bind("userId", userId)
                .execute()
        }
    }

    private fun mapNotification(rs: java.sql.ResultSet): Notification {
        val readAt = rs.getTimestamp("read_at")?.toInstant()
        val createdAt = rs.getTimestamp("created_at")?.toInstant() ?: Instant.now()
        return Notification(
            id = rs.getObject("id", UUID::class.java),
            userId = rs.getObject("user_id", UUID::class.java),
            title = rs.getString("title"),
            body = rs.getString("body"),
            type = rs.getString("type") ?: "info",
            readAt = readAt,
            createdAt = createdAt,
        )
    }
}
