package io.github.rygel.outerstellar.platform.persistence

import java.time.Instant
import java.util.UUID

data class Notification(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val title: String,
    val body: String,
    val type: String = "info", // info | success | warning | danger
    val readAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
) {
    val isRead: Boolean
        get() = readAt != null
}

interface NotificationRepository {
    fun save(notification: Notification)

    fun findByUserId(userId: UUID, limit: Int = 50): List<Notification>

    fun countUnread(userId: UUID): Int

    fun markRead(id: UUID, userId: UUID)

    fun markAllRead(userId: UUID)

    fun delete(id: UUID, userId: UUID)
}
