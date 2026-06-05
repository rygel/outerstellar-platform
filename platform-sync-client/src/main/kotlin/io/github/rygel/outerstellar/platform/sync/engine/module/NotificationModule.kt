package io.github.rygel.outerstellar.platform.sync.engine.module

import io.github.rygel.outerstellar.platform.model.NotificationSummary

data class NotificationState(val notifications: List<NotificationSummary> = emptyList()) {
    val unreadCount: Int
        get() = notifications.count { !it.read }
}

interface NotificationListener {
    fun onNotificationStateChanged(state: NotificationState) {}

    fun onSessionExpired() {}
}

interface NotificationModule {
    val notificationState: NotificationState

    fun addListener(listener: NotificationListener)

    fun removeListener(listener: NotificationListener)

    fun loadNotifications()

    fun markNotificationRead(notificationId: String)

    fun markAllNotificationsRead()

    fun clearState() {}
}
