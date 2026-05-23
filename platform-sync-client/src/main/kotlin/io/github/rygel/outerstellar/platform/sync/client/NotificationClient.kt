package io.github.rygel.outerstellar.platform.sync.client

import io.github.rygel.outerstellar.platform.model.NotificationSummary

interface NotificationClient {
    fun listNotifications(): List<NotificationSummary>

    fun markNotificationRead(notificationId: String)

    fun markAllNotificationsRead()
}
