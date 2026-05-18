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
) : SyncEngine {
    private val logger = LoggerFactory.getLogger(DesktopSyncEngine::class.java)

    private val _state = AtomicReference(EngineState())
    override val state: EngineState
        get() = _state.get()

    val listeners = CopyOnWriteArrayList<EngineListener>()

    private val syncInProgress = AtomicBoolean(false)
    private var autoSyncExecutor: ScheduledExecutorService? = null
    private val autoSyncIntervalMinutes: Long = 5L

    init {
        connectivityChecker?.addObserver { online -> updateState { it.copy(isOnline = online) } }
    }

    override fun addListener(listener: EngineListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: EngineListener) {
        listeners.remove(listener)
    }

    private fun updateState(transform: (EngineState) -> EngineState) {
        val newState = _state.updateAndGet(transform)
        listeners.forEach { it.onStateChanged(newState) }
    }

    override fun login(username: String, password: String): Result<Unit> =
        runGuardedResult(
            "login",
            onError = { e ->
                updateState { it.copy(status = "Login failed: ${e.message}") }
                notifier?.notifyFailure("Login failed: ${e.message}")
            },
        ) {
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
        }

    override fun register(username: String, password: String): Result<Unit> =
        runGuardedResult(
            "register",
            onError = { e ->
                updateState { it.copy(status = "Registration failed: ${e.message}") }
                notifier?.notifyFailure("Registration failed: ${e.message}")
            },
        ) {
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
        }

    override fun logout() {
        stopAutoSync()
        syncService.logout()
        updateState { EngineState(isOnline = it.isOnline, status = "Logged out") }
        val username = state.userName
        if (username.isNotBlank()) {
            analytics.track(username, "user_logout")
        }
    }

    override fun sync(isAuto: Boolean): Result<Unit> {
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

    override fun changePassword(currentPassword: String, newPassword: String): Result<Unit> =
        runGuardedResult(
            "changePassword",
            onError = { e -> notifier?.notifyFailure("Password change failed: ${e.message}") },
        ) {
            syncService.changePassword(currentPassword, newPassword)
            analytics.track(state.userName, "password_changed")
            notifier?.notifySuccess("Password changed")
            Result.success(Unit)
        }

    override fun requestPasswordReset(email: String): Result<Unit> {
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

    override fun resetPassword(token: String, newPassword: String): Result<Unit> {
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

    override fun loadUsers() =
        runGuarded("loadUsers") {
            val users = syncService.listUsers()
            updateState { it.copy(adminUsers = users) }
        }

    override fun setUserEnabled(userId: String, enabled: Boolean): Result<Unit> =
        runGuardedResult("setUserEnabled") {
            syncService.setUserEnabled(userId, enabled)
            loadUsers()
            analytics.track(state.userName, "user_enabled_changed", mapOf("userId" to userId, "enabled" to enabled))
            Result.success(Unit)
        }

    override fun setUserRole(userId: String, role: String): Result<Unit> =
        runGuardedResult("setUserRole") {
            syncService.setUserRole(userId, role)
            loadUsers()
            analytics.track(state.userName, "user_role_changed", mapOf("userId" to userId, "role" to role))
            Result.success(Unit)
        }

    override fun loadNotifications() =
        runGuarded("loadNotifications") {
            val notifications = syncService.listNotifications()
            updateState { it.copy(notifications = notifications) }
        }

    override fun markNotificationRead(notificationId: String) =
        runGuarded("markNotificationRead") {
            syncService.markNotificationRead(notificationId)
            loadNotifications()
        }

    override fun markAllNotificationsRead() =
        runGuarded("markAllNotificationsRead") {
            syncService.markAllNotificationsRead()
            loadNotifications()
        }

    override fun loadProfile() =
        runGuarded("loadProfile") {
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
        }

    override fun updateProfile(email: String, username: String?, avatarUrl: String?): Result<Unit> =
        runGuardedResult(
            "updateProfile",
            onError = { e -> notifier?.notifyFailure("Profile update failed: ${e.message}") },
        ) {
            syncService.updateProfile(email, username, avatarUrl)
            loadData()
            loadProfile()
            analytics.track(state.userName, "profile_updated")
            notifier?.notifySuccess("Profile updated")
            Result.success(Unit)
        }

    override fun deleteAccount(): Result<Unit> =
        runGuardedResult(
            "deleteAccount",
            onError = { e -> notifier?.notifyFailure("Account deletion failed: ${e.message}") },
        ) {
            val username = state.userName
            syncService.deleteAccount()
            stopAutoSync()
            syncService.logout()
            analytics.track(username, "account_deleted")
            updateState { EngineState(isOnline = it.isOnline, status = "Account deleted") }
            notifier?.notifySuccess("Account deleted")
            Result.success(Unit)
        }

    override fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Result<Unit> =
        runGuardedResult("updateNotificationPreferences") {
            syncService.updateNotificationPreferences(emailEnabled, pushEnabled)
            updateState { it.copy(emailNotificationsEnabled = emailEnabled, pushNotificationsEnabled = pushEnabled) }
            Result.success(Unit)
        }

    override fun createLocalMessage(author: String, content: String): Result<Unit> =
        runGuardedResult("createLocalMessage") {
            messageService.createLocalMessage(author, content)
            loadMessages()
            Result.success(Unit)
        }

    override fun resolveConflict(syncId: String, strategy: ConflictStrategy) =
        runGuarded("resolveConflict") {
            messageService.resolveConflict(syncId, strategy)
            loadMessages()
        }

    override fun createContact(
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

    override fun setSearchQuery(query: String) {
        updateState { it.copy(searchQuery = query) }
        loadData()
    }

    override fun loadData() {
        loadMessages()
        loadContacts()
    }

    override fun loadMessages() {
        try {
            val query = state.searchQuery.ifBlank { null }
            val result = messageService.listMessages(query = query)
            updateState { it.copy(messages = result.items) }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to load messages", e)
            fireError("loadMessages", e.message ?: "Unknown error")
        }
    }

    override fun loadContacts() {
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

    override fun startAutoSync() {
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

    override fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    override fun startConnectivityChecker() {
        connectivityChecker?.start()
    }

    override fun stopConnectivityChecker() {
        connectivityChecker?.stop()
    }

    override fun shutdown() {
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

    internal inline fun runGuarded(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Unit,
    ) {
        try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to {}", operation, e)
            onError(e)
            fireError(operation, e.message ?: "Unknown error")
        }
    }

    internal inline fun runGuardedResult(
        operation: String,
        crossinline onError: (Exception) -> Unit = {},
        block: () -> Result<Unit>,
    ): Result<Unit> {
        return try {
            block()
        } catch (e: SessionExpiredException) {
            handleSessionExpired(e)
            Result.failure(e)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.warn("Failed to {}", operation, e)
            fireError(operation, e.message ?: "Unknown error")
            onError(e)
            Result.failure(e)
        }
    }
}
