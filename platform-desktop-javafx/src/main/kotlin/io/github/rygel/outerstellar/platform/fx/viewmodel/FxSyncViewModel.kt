package io.github.rygel.outerstellar.platform.fx.viewmodel

import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.engine.EngineListener
import io.github.rygel.outerstellar.platform.sync.engine.EngineState
import io.github.rygel.outerstellar.platform.sync.engine.SyncEngine
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task

@Suppress("TooManyFunctions")
class FxSyncViewModel(private val engine: SyncEngine) {

    val userName = SimpleStringProperty("")
    val userEmail = SimpleStringProperty("")
    val userAvatarUrl = SimpleObjectProperty<String?>(null)
    val userRole = SimpleObjectProperty<String?>(null)
    val isLoggedIn = SimpleBooleanProperty(false)
    val isOnline = SimpleBooleanProperty(true)
    val isSyncing = SimpleBooleanProperty(false)
    val status = SimpleStringProperty("Ready")
    val searchQuery = SimpleStringProperty("")
    val author = SimpleStringProperty("")
    val content = SimpleStringProperty("")
    val emailNotificationsEnabled = SimpleBooleanProperty(false)
    val pushNotificationsEnabled = SimpleBooleanProperty(false)

    val messages: ObservableList<MessageSummary> = FXCollections.observableArrayList()
    val contacts: ObservableList<ContactSummary> = FXCollections.observableArrayList()
    val adminUsers: ObservableList<UserSummary> = FXCollections.observableArrayList()
    val notifications: ObservableList<NotificationSummary> = FXCollections.observableArrayList()

    val unreadNotificationCount = SimpleIntegerProperty(0)

    private val listener =
        object : EngineListener {
            override fun onStateChanged(newState: EngineState) {
                Platform.runLater { syncProperties(newState) }
            }

            override fun onSessionExpired() {
                Platform.runLater {
                    isLoggedIn.set(false)
                    userName.set("")
                    userRole.set(null)
                    status.set("Session expired")
                }
            }
        }

    init {
        engine.addListener(listener)
        syncProperties(engine.state)
    }

    private fun syncProperties(state: EngineState) {
        userName.set(state.userName)
        userEmail.set(state.userEmail)
        userAvatarUrl.set(state.userAvatarUrl)
        userRole.set(state.userRole)
        isLoggedIn.set(state.isLoggedIn)
        isOnline.set(state.isOnline)
        isSyncing.set(state.isSyncing)
        if (state.status.isNotBlank()) status.set(state.status)
        searchQuery.set(state.searchQuery)
        emailNotificationsEnabled.set(state.emailNotificationsEnabled)
        pushNotificationsEnabled.set(state.pushNotificationsEnabled)
        messages.setAll(state.messages)
        contacts.setAll(state.contacts)
        adminUsers.setAll(state.adminUsers)
        notifications.setAll(state.notifications)
        unreadNotificationCount.set(state.notifications.count { !it.read })
    }

    fun login(username: String, password: String): Task<Result<Unit>> =
        task("login") { engine.login(username, password) }

    fun register(username: String, password: String): Task<Result<Unit>> =
        task("register") { engine.register(username, password) }

    fun logout(): Task<Unit> = task("logout") { engine.logout() }

    fun sync(isAuto: Boolean = false): Task<Result<Unit>> = task("sync") { engine.sync(isAuto) }

    fun changePassword(currentPassword: String, newPassword: String): Task<Result<Unit>> =
        task("changePassword") { engine.changePassword(currentPassword, newPassword) }

    fun requestPasswordReset(email: String): Task<Result<Unit>> =
        task("requestPasswordReset") { engine.requestPasswordReset(email) }

    fun resetPassword(token: String, newPassword: String): Task<Result<Unit>> =
        task("resetPassword") { engine.resetPassword(token, newPassword) }

    fun loadUsers(): Task<Unit> = task("loadUsers") { engine.loadUsers() }

    fun setUserEnabled(userId: String, enabled: Boolean): Task<Result<Unit>> =
        task("setUserEnabled") { engine.setUserEnabled(userId, enabled) }

    fun setUserRole(userId: String, role: String): Task<Result<Unit>> =
        task("setUserRole") { engine.setUserRole(userId, role) }

    fun loadNotifications(): Task<Unit> = task("loadNotifications") { engine.loadNotifications() }

    fun markNotificationRead(notificationId: String): Task<Unit> =
        task("markNotificationRead") { engine.markNotificationRead(notificationId) }

    fun markAllNotificationsRead(): Task<Unit> = task("markAllNotificationsRead") { engine.markAllNotificationsRead() }

    fun loadProfile(): Task<Unit> = task("loadProfile") { engine.loadProfile() }

    fun updateProfile(email: String, username: String?, avatarUrl: String?): Task<Result<Unit>> =
        task("updateProfile") { engine.updateProfile(email, username, avatarUrl) }

    fun deleteAccount(): Task<Result<Unit>> = task("deleteAccount") { engine.deleteAccount() }

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Task<Result<Unit>> =
        task("updateNotificationPreferences") { engine.updateNotificationPreferences(emailEnabled, pushEnabled) }

    fun createLocalMessage(author: String, content: String): Task<Result<Unit>> =
        task("createLocalMessage") { engine.createLocalMessage(author, content) }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy): Task<Unit> =
        task("resolveConflict") { engine.resolveConflict(syncId, strategy) }

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Task<Result<Unit>> =
        task("createContact") {
            engine.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
        }

    fun loadData(): Task<Unit> = task("loadData") { engine.loadData() }

    fun loadMessages(): Task<Unit> = task("loadMessages") { engine.loadMessages() }

    fun loadContacts(): Task<Unit> = task("loadContacts") { engine.loadContacts() }

    fun startAutoSync() {
        engine.startAutoSync()
    }

    fun stopAutoSync() {
        engine.stopAutoSync()
    }

    fun startConnectivityChecker() {
        engine.startConnectivityChecker()
    }

    fun stopConnectivityChecker() {
        engine.stopConnectivityChecker()
    }

    fun shutdown() {
        engine.removeListener(listener)
        engine.shutdown()
    }

    @Suppress("UnusedParameter")
    private fun <T> task(operation: String, block: () -> T): Task<T> {
        return object : Task<T>() {
            override fun call(): T = block()
        }
    }
}
