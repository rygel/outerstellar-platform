@file:Suppress("TooManyFunctions")

package io.github.rygel.outerstellar.platform.sync.engine

import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.sync.SyncService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory

class DesktopSyncEngine(
    private val syncService: SyncService,
    private val messageService: MessageService,
    private val contactService: ContactService? = null,
    private val analytics: AnalyticsService,
    private val connectivityChecker: ConnectivityChecker? = null,
    private val notifier: EngineNotifier? = null,
) {
    private val logger = LoggerFactory.getLogger(DesktopSyncEngine::class.java)

    private val _state = AtomicReference(EngineState())
    val state: EngineState
        get() = _state.get()

    val listeners = CopyOnWriteArrayList<EngineListener>()

    private val syncInProgress = AtomicBoolean(false)
    private var autoSyncExecutor: ScheduledExecutorService? = null
    private val autoSyncIntervalMinutes: Long = 5L

    init {
        connectivityChecker?.addObserver { online -> updateState { it.copy(isOnline = online) } }
    }

    fun addListener(listener: EngineListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: EngineListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (EngineState) -> EngineState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onStateChanged(newState) }
    }

    fun login(username: String, password: String): Result<Unit> {
        return try {
            val auth = syncService.login(username, password)
            updateState {
                it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role, status = "Logged in")
            }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_login")
            startAutoSync()
            loadData()
            notifier?.notifySuccess("Logged in as ${auth.username}")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Login failed", e)
            updateState { it.copy(status = "Login failed: ${e.message}") }
            notifier?.notifyFailure("Login failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun register(username: String, password: String): Result<Unit> {
        return try {
            val auth = syncService.register(username, password)
            updateState {
                it.copy(isLoggedIn = true, userName = auth.username, userRole = auth.role, status = "Registered")
            }
            analytics.identify(auth.username, mapOf("role" to auth.role))
            analytics.track(auth.username, "user_register")
            startAutoSync()
            loadData()
            notifier?.notifySuccess("Registered as ${auth.username}")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Registration failed", e)
            updateState { it.copy(status = "Registration failed: ${e.message}") }
            notifier?.notifyFailure("Registration failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun logout() {
        stopAutoSync()
        syncService.logout()
        updateState { EngineState(isOnline = it.isOnline, status = "Logged out") }
        val username = state.userName
        if (username.isNotBlank()) {
            analytics.track(username, "user_logout")
        }
    }

    fun sync(isAuto: Boolean = false): Result<Unit> {
        if (!state.isLoggedIn) {
            return Result.failure(IllegalStateException("Not logged in"))
        }
        if (state.isOnline == false) {
            if (!isAuto) {
                notifier?.notifyFailure("Cannot sync while offline")
            }
            return Result.failure(IllegalStateException("Offline"))
        }
        if (!syncInProgress.compareAndSet(false, true)) {
            return Result.success(Unit)
        }
        updateState { it.copy(isSyncing = true, status = if (isAuto) "Auto-syncing..." else "Syncing...") }
        return try {
            val stats = syncService.sync()
            updateState {
                it.copy(
                    isSyncing = false,
                    status = buildSyncStatusMessage(stats.pushedCount, stats.pulledCount, stats.conflictCount),
                )
            }
            if (isAuto) {
                analytics.track(state.userName, "auto_sync")
            } else {
                analytics.track(state.userName, "manual_sync")
            }
            loadData()
            if (!isAuto) {
                notifier?.notifySuccess(state.status)
            }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false) }
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            syncInProgress.set(false)
            updateState { it.copy(isSyncing = false, status = "Sync failed: ${e.message}") }
            if (!isAuto) {
                notifier?.notifyFailure("Sync failed: ${e.message}")
                fireError("sync", e.message ?: "Unknown error")
            }
            Result.failure(e)
        } finally {
            syncInProgress.set(false)
        }
    }

    fun changePassword(currentPassword: String, newPassword: String): Result<Unit> {
        return try {
            syncService.changePassword(currentPassword, newPassword)
            analytics.track(state.userName, "password_changed")
            notifier?.notifySuccess("Password changed")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Password change failed", e)
            notifier?.notifyFailure("Password change failed: ${e.message}")
            fireError("changePassword", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun loadUsers() {
        try {
            val users = syncService.listUsers()
            updateState { it.copy(adminUsers = users) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load users", e)
            fireError("loadUsers", e.message ?: "Unknown error")
        }
    }

    fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit> {
        return try {
            syncService.setUserEnabled(userId, enabled)
            loadUsers()
            analytics.track(state.userName, "user_enabled_changed", mapOf("userId" to userId, "enabled" to enabled))
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to set user enabled", e)
            fireError("setUserEnabled", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun setUserRole(userId: String, role: String): Result<Unit> {
        return try {
            syncService.setUserRole(userId, role)
            loadUsers()
            analytics.track(state.userName, "user_role_changed", mapOf("userId" to userId, "role" to role))
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to set user role", e)
            fireError("setUserRole", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun requestPasswordReset(email: String): Result<Unit> {
        return try {
            syncService.requestPasswordReset(email)
            notifier?.notifySuccess("Password reset email sent")
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Password reset request failed", e)
            notifier?.notifyFailure("Password reset request failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun resetPassword(token: String, newPassword: String): Result<Unit> {
        return try {
            syncService.resetPassword(token, newPassword)
            notifier?.notifySuccess("Password has been reset")
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Password reset failed", e)
            notifier?.notifyFailure("Password reset failed: ${e.message}")
            Result.failure(e)
        }
    }

    fun loadNotifications() {
        try {
            val notifications = syncService.listNotifications()
            updateState { it.copy(notifications = notifications) }
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load notifications", e)
            fireError("loadNotifications", e.message ?: "Unknown error")
        }
    }

    fun markNotificationRead(notificationId: String) {
        try {
            syncService.markNotificationRead(notificationId)
            loadNotifications()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to mark notification read", e)
            fireError("markNotificationRead", e.message ?: "Unknown error")
        }
    }

    fun markAllNotificationsRead() {
        try {
            syncService.markAllNotificationsRead()
            loadNotifications()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to mark all notifications read", e)
            fireError("markAllNotificationsRead", e.message ?: "Unknown error")
        }
    }

    fun loadProfile() {
        try {
            val profile = syncService.fetchProfile()
            updateState {
                it.copy(
                    userName = profile.username,
                    userEmail = profile.email,
                    userAvatarUrl = profile.avatarUrl,
                    emailNotificationsEnabled = profile.emailNotificationsEnabled,
                    pushNotificationsEnabled = profile.pushNotificationsEnabled,
                )
            }
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load profile", e)
            fireError("loadProfile", e.message ?: "Unknown error")
        }
    }

    fun updateProfile(email: String, username: String? = null, avatarUrl: String? = null): Result<Unit> {
        return try {
            syncService.updateProfile(email, username, avatarUrl)
            loadProfile()
            analytics.track(state.userName, "profile_updated")
            notifier?.notifySuccess("Profile updated")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to update profile", e)
            notifier?.notifyFailure("Profile update failed: ${e.message}")
            fireError("updateProfile", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit> {
        return try {
            syncService.updateNotificationPreferences(emailEnabled, pushEnabled)
            updateState { it.copy(emailNotificationsEnabled = emailEnabled, pushNotificationsEnabled = pushEnabled) }
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to update notification preferences", e)
            fireError("updateNotificationPreferences", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun deleteAccount(): Result<Unit> {
        return try {
            val username = state.userName
            syncService.deleteAccount()
            stopAutoSync()
            syncService.logout()
            analytics.track(username, "account_deleted")
            updateState { EngineState(isOnline = it.isOnline, status = "Account deleted") }
            notifier?.notifySuccess("Account deleted")
            Result.success(Unit)
        } catch (e: SessionExpiredException) {
            handleSessionExpired()
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to delete account", e)
            notifier?.notifyFailure("Account deletion failed: ${e.message}")
            fireError("deleteAccount", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun createLocalMessage(author: String, content: String): Result<Unit> {
        return try {
            messageService.createLocalMessage(author, content)
            loadMessages()
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to create local message", e)
            fireError("createLocalMessage", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        try {
            messageService.resolveConflict(syncId, strategy)
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to resolve conflict", e)
            fireError("resolveConflict", e.message ?: "Unknown error")
        }
    }

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Result<Unit> {
        val svc = contactService
        if (svc == null) {
            return Result.failure(IllegalStateException("Contact service not available"))
        }
        return try {
            svc.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
            loadContacts()
            analytics.track(state.userName, "contact_created", mapOf("name" to name))
            Result.success(Unit)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to create contact", e)
            fireError("createContact", e.message ?: "Unknown error")
            Result.failure(e)
        }
    }

    fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadData()
    }

    fun loadData() {
        loadMessages()
        loadContacts()
    }

    fun loadMessages() {
        try {
            val query = state.searchQuery.ifBlank { null }
            val result = messageService.listMessages(query = query)
            updateState { it.copy(messages = result.items) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load messages", e)
            fireError("loadMessages", e.message ?: "Unknown error")
        }
    }

    fun loadContacts() {
        val svc = contactService ?: return
        try {
            val query = state.searchQuery.ifBlank { null }
            val contacts = svc.listContacts(query = query)
            updateState { it.copy(contacts = contacts) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load contacts", e)
            fireError("loadContacts", e.message ?: "Unknown error")
        }
    }

    fun startAutoSync() {
        stopAutoSync()
        autoSyncExecutor =
            Executors.newSingleThreadScheduledExecutor { runnable ->
                    Thread(runnable, "auto-sync").also { it.isDaemon = true }
                }
                .also { executor ->
                    executor.scheduleAtFixedRate(
                        { sync(isAuto = true) },
                        autoSyncIntervalMinutes,
                        autoSyncIntervalMinutes,
                        TimeUnit.MINUTES,
                    )
                }
    }

    fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    fun startConnectivityChecker() {
        connectivityChecker?.start()
    }

    fun stopConnectivityChecker() {
        connectivityChecker?.stop()
    }

    fun shutdown() {
        stopAutoSync()
        stopConnectivityChecker()
        listeners.clear()
    }

    private fun handleSessionExpired(e: Exception? = null) {
        if (e != null) {
            logger.warn("Session expired: ${e.message}", e)
        }
        stopAutoSync()
        syncService.logout()
        updateState { EngineState(isOnline = it.isOnline, status = "Session expired") }
        listeners.forEach { it.onSessionExpired() }
        notifier?.notifyFailure("Session expired. Please log in again.")
    }

    private fun fireError(operation: String, message: String) {
        listeners.forEach { it.onError(operation, message) }
    }

    private fun buildSyncStatusMessage(pushed: Int, pulled: Int, conflicts: Int): String {
        val parts = mutableListOf<String>()
        if (pushed > 0) parts.add("pushed $pushed")
        if (pulled > 0) parts.add("pulled $pulled")
        if (conflicts > 0) parts.add("$conflicts conflict(s)")
        return if (parts.isEmpty()) "Everything up to date" else "Synced: ${parts.joinToString(", ")}"
    }
}
