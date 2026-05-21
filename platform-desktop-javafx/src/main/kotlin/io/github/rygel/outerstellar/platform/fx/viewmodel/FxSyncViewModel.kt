package io.github.rygel.outerstellar.platform.fx.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.NotificationSummary
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminListener
import io.github.rygel.outerstellar.platform.sync.engine.module.AdminModule
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthListener
import io.github.rygel.outerstellar.platform.sync.engine.module.AuthModule
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationListener
import io.github.rygel.outerstellar.platform.sync.engine.module.NotificationModule
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileListener
import io.github.rygel.outerstellar.platform.sync.engine.module.ProfileModule
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataListener
import io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataModule
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.concurrent.Task

class FxSyncViewModel(
    private val authModule: AuthModule,
    private val syncDataModule: SyncDataModule,
    private val profileModule: ProfileModule,
    private val adminModule: AdminModule,
    private val notificationModule: NotificationModule,
    private val i18n: I18nService,
) {

    val userName = SimpleStringProperty(authModule.authState.userName)
    val userEmail = SimpleStringProperty(profileModule.profileState.userEmail)
    val userAvatarUrl = SimpleObjectProperty<String?>(profileModule.profileState.userAvatarUrl)
    val userRole = SimpleObjectProperty<String?>(authModule.authState.userRole)
    val isLoggedIn = SimpleBooleanProperty(authModule.authState.isLoggedIn)
    val isOnline = SimpleBooleanProperty(syncDataModule.syncDataState.isOnline)
    val isSyncing = SimpleBooleanProperty(syncDataModule.syncDataState.isSyncing)
    val status = SimpleStringProperty("Ready")
    val searchQuery = SimpleStringProperty(syncDataModule.syncDataState.searchQuery)
    val author = SimpleStringProperty("")
    val content = SimpleStringProperty("")
    val emailNotificationsEnabled = SimpleBooleanProperty(profileModule.profileState.emailNotificationsEnabled)
    val pushNotificationsEnabled = SimpleBooleanProperty(profileModule.profileState.pushNotificationsEnabled)

    val messages: ObservableList<MessageSummary> = FXCollections.observableArrayList()
    val contacts: ObservableList<ContactSummary> = FXCollections.observableArrayList()
    val adminUsers: ObservableList<UserSummary> = FXCollections.observableArrayList()
    val notifications: ObservableList<NotificationSummary> = FXCollections.observableArrayList()

    val unreadNotificationCount = SimpleIntegerProperty(0)

    private val syncDataListener =
        object : SyncDataListener {
            override fun onSyncDataStateChanged(
                state: io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataState
            ) {
                Platform.runLater {
                    isOnline.set(state.isOnline)
                    isSyncing.set(state.isSyncing)
                    if (state.syncStatus.isNotBlank()) status.set(state.syncStatus)
                    messages.setAll(state.messages)
                    contacts.setAll(state.contacts)
                }
            }
        }

    private val authListener =
        object : AuthListener {
            override fun onAuthStateChanged(state: io.github.rygel.outerstellar.platform.sync.engine.module.AuthState) {
                Platform.runLater {
                    isLoggedIn.set(state.isLoggedIn)
                    userName.set(state.userName)
                    userRole.set(state.userRole)
                }
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

    private val profileListener =
        object : ProfileListener {
            override fun onProfileStateChanged(
                state: io.github.rygel.outerstellar.platform.sync.engine.module.ProfileState
            ) {
                Platform.runLater {
                    userEmail.set(state.userEmail)
                    userAvatarUrl.set(state.userAvatarUrl)
                    emailNotificationsEnabled.set(state.emailNotificationsEnabled)
                    pushNotificationsEnabled.set(state.pushNotificationsEnabled)
                }
            }
        }

    private val adminListener =
        object : AdminListener {
            override fun onAdminStateChanged(
                state: io.github.rygel.outerstellar.platform.sync.engine.module.AdminState
            ) {
                Platform.runLater { adminUsers.setAll(state.adminUsers) }
            }
        }

    private val notificationListener =
        object : NotificationListener {
            override fun onNotificationStateChanged(
                state: io.github.rygel.outerstellar.platform.sync.engine.module.NotificationState
            ) {
                Platform.runLater {
                    notifications.setAll(state.notifications)
                    unreadNotificationCount.set(state.unreadCount)
                }
            }
        }

    init {
        syncDataModule.addListener(syncDataListener)
        authModule.addListener(authListener)
        profileModule.addListener(profileListener)
        adminModule.addListener(adminListener)
        notificationModule.addListener(notificationListener)
        syncFromModules()
    }

    private fun syncFromModules() {
        val auth = authModule.authState
        userName.set(auth.userName)
        isLoggedIn.set(auth.isLoggedIn)
        userRole.set(auth.userRole)

        val sd = syncDataModule.syncDataState
        isOnline.set(sd.isOnline)
        isSyncing.set(sd.isSyncing)
        if (sd.syncStatus.isNotBlank()) status.set(sd.syncStatus)
        messages.setAll(sd.messages)
        contacts.setAll(sd.contacts)

        val prof = profileModule.profileState
        userEmail.set(prof.userEmail)
        userAvatarUrl.set(prof.userAvatarUrl)
        emailNotificationsEnabled.set(prof.emailNotificationsEnabled)
        pushNotificationsEnabled.set(prof.pushNotificationsEnabled)

        adminUsers.setAll(adminModule.adminState.adminUsers)
        notifications.setAll(notificationModule.notificationState.notifications)
        unreadNotificationCount.set(notificationModule.notificationState.unreadCount)
    }

    fun login(username: String, password: String): Task<Result<Unit>> = task { authModule.login(username, password) }

    fun register(username: String, password: String): Task<Result<Unit>> = task {
        authModule.register(username, password)
    }

    fun logout(): Task<Unit> = task { authModule.logout() }

    fun sync(isAuto: Boolean = false): Task<Result<Unit>> = task { syncDataModule.sync(isAuto) }

    fun changePassword(currentPassword: String, newPassword: String): Task<Result<Unit>> = task {
        authModule.changePassword(currentPassword, newPassword)
    }

    fun requestPasswordReset(email: String): Task<Result<Unit>> = task { authModule.requestPasswordReset(email) }

    fun resetPassword(token: String, newPassword: String): Task<Result<Unit>> = task {
        authModule.resetPassword(token, newPassword)
    }

    fun loadUsers(): Task<Unit> = task { adminModule.loadUsers() }

    fun setUserEnabled(userId: String, enabled: Boolean): Task<Result<Unit>> = task {
        adminModule.setUserEnabled(userId, enabled)
    }

    fun setUserRole(userId: String, role: String): Task<Result<Unit>> = task { adminModule.setUserRole(userId, role) }

    fun loadNotifications(): Task<Unit> = task { notificationModule.loadNotifications() }

    fun markNotificationRead(notificationId: String): Task<Unit> = task {
        notificationModule.markNotificationRead(notificationId)
    }

    fun markAllNotificationsRead(): Task<Unit> = task { notificationModule.markAllNotificationsRead() }

    fun loadProfile(): Task<Unit> = task { profileModule.loadProfile() }

    fun updateProfile(email: String, username: String?, avatarUrl: String?): Task<Result<Unit>> = task {
        profileModule.updateProfile(email, username, avatarUrl)
    }

    fun deleteAccount(currentPassword: String): Task<Result<Unit>> = task {
        profileModule.deleteAccount(currentPassword)
    }

    fun updateNotificationPreferences(emailEnabled: Boolean, pushEnabled: Boolean): Task<Result<Unit>> = task {
        profileModule.updateNotificationPreferences(emailEnabled, pushEnabled)
    }

    fun createLocalMessage(author: String, content: String): Task<Result<Unit>> = task {
        syncDataModule.createLocalMessage(author, content)
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy): Task<Unit> = task {
        syncDataModule.resolveConflict(syncId, strategy)
    }

    fun createContact(
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Task<Result<Unit>> = task {
        syncDataModule.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
    }

    fun updateContact(
        syncId: String,
        name: String,
        emails: List<String>,
        phones: List<String>,
        socialMedia: List<String>,
        company: String,
        companyAddress: String,
        department: String,
    ): Task<Result<Unit>> = task {
        syncDataModule.updateContact(syncId, name, emails, phones, socialMedia, company, companyAddress, department)
    }

    fun loadData(): Task<Unit> = task { syncDataModule.loadData() }

    fun loadMessages(): Task<Unit> = task { syncDataModule.loadMessages() }

    fun loadContacts(): Task<Unit> = task { syncDataModule.loadContacts() }

    fun startAutoSync() {
        syncDataModule.startAutoSync()
    }

    fun stopAutoSync() {
        syncDataModule.stopAutoSync()
    }

    fun shutdown() {
        syncDataModule.removeListener(syncDataListener)
        authModule.removeListener(authListener)
        profileModule.removeListener(profileListener)
        adminModule.removeListener(adminListener)
        notificationModule.removeListener(notificationListener)
    }

    private fun <T> task(block: () -> T): Task<T> {
        return object : Task<T>() {
            override fun call(): T = block()
        }
    }
}
