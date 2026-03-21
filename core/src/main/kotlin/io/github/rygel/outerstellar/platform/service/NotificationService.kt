package io.github.rygel.outerstellar.platform.service

import io.github.rygel.outerstellar.platform.persistence.Notification
import io.github.rygel.outerstellar.platform.persistence.NotificationRepository
import java.util.UUID

class NotificationService(private val repository: NotificationRepository) {

    fun create(userId: UUID, title: String, body: String, type: String = "info") {
        repository.save(Notification(userId = userId, title = title, body = body, type = type))
    }

    fun listForUser(userId: UUID, limit: Int = 50): List<Notification> = repository.findByUserId(userId, limit)

    fun countUnread(userId: UUID): Int = repository.countUnread(userId)

    fun markRead(id: UUID, userId: UUID) = repository.markRead(id, userId)

    fun markAllRead(userId: UUID) = repository.markAllRead(userId)

    fun delete(id: UUID, userId: UUID) = repository.delete(id, userId)
}
