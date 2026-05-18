package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.model.ConflictStrategy

@Suppress("TooManyFunctions")
interface SyncEngine {

    val state: EngineState

    fun addListener(listener: EngineListener)

    fun removeListener(listener: EngineListener)

    fun login(username: String, password: String): Result<Unit>

    fun register(username: String, password: String): Result<Unit>

    fun logout()

    fun sync(isAuto: Boolean = false): Result<Unit>

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit>

    fun requestPasswordReset(email: String): Result<Unit>

    fun resetPassword(token: String, newPassword: String): Result<Unit>

    fun loadUsers()

    fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit>

    fun setUserRole(userId: String, role: String): Result<Unit>

    fun loadNotifications()

    fun markNotificationRead(notificationId: String)

    fun markAllNotificationsRead()

    fun loadProfile()

    fun updateProfile(email: String, username: String? = null, avatarUrl: String? = null): Result<Unit>

    fun deleteAccount(): Result<Unit>

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit>

    fun createLocalMessage(author: String, content: String): Result<Unit>

    fun resolveConflict(syncId: String, strategy: ConflictStrategy)

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit>

    fun setSearchQuery(query: String)

    fun loadData()

    fun loadMessages()

    fun loadContacts()

    fun startAutoSync()

    fun stopAutoSync()

    fun startConnectivityChecker()

    fun stopConnectivityChecker()

    fun shutdown()
}
