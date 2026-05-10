package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.UserSummary

data class EngineState(
    val isLoggedIn: Boolean = false,
    val userName: String = "",
    val userRole: String? = null,
    val isOnline: Boolean = true,
    val isSyncing: Boolean = false,
    val status: String = "",
    val messages: List<MessageSummary> = emptyList(),
    val contacts: List<ContactSummary> = emptyList(),
    val adminUsers: List<UserSummary> = emptyList(),
    val notifications: List<NotificationSummary> = emptyList(),
    val userEmail: String = "",
    val userAvatarUrl: String? = null,
    val emailNotificationsEnabled: Boolean = true,
    val pushNotificationsEnabled: Boolean = true,
    val searchQuery: String = "",
) {
    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }
}
