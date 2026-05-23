package io.github.rygel.outerstellar.platform.swing.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.OuterstellarException
import io.github.rygel.outerstellar.platform.model.UserRole
import io.github.rygel.outerstellar.platform.model.ValidationException
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
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingWorker
import javax.swing.Timer

@Suppress("TooManyFunctions")
class SyncViewModel(
    private val authModule: AuthModule,
    private val syncDataModule: SyncDataModule,
    private val profileModule: ProfileModule,
    private val adminModule: AdminModule,
    private val notificationModule: NotificationModule,
    private var i18nService: I18nService,
) {
    private val observers = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isOnline: Boolean = syncDataModule.syncDataState.isOnline
        private set

    @Volatile
    var messages = syncDataModule.syncDataState.messages
        private set

    @Volatile
    var contacts = syncDataModule.syncDataState.contacts
        private set

    @Volatile
    var status: String = i18nService.translate("swing.status.ready")
        private set

    @Volatile
    var isSyncing: Boolean = syncDataModule.syncDataState.isSyncing
        private set

    @Volatile
    var userName: String = authModule.authState.userName
        private set

    @Volatile
    var isLoggedIn: Boolean = authModule.authState.isLoggedIn
        private set

    @Volatile
    var userRole: String? = authModule.authState.userRole
        private set

    @Volatile
    var adminUsers = adminModule.adminState.adminUsers
        private set

    @Volatile
    var notifications = notificationModule.notificationState.notifications
        private set

    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }

    @Volatile
    var userEmail: String = profileModule.profileState.userEmail
        private set

    @Volatile
    var userAvatarUrl: String? = profileModule.profileState.userAvatarUrl
        private set

    @Volatile
    var emailNotificationsEnabled: Boolean = profileModule.profileState.emailNotificationsEnabled
        private set

    @Volatile
    var pushNotificationsEnabled: Boolean = profileModule.profileState.pushNotificationsEnabled
        private set

    var author: String = i18nService.translate("swing.author.default")
    var content: String = ""
    var searchQuery: String = ""
        set(value) {
            if (field == value) return
            field = value
            searchDebounceTimer?.stop()
            searchDebounceTimer =
                Timer(300) { loadMessages() }
                    .apply {
                        isRepeats = false
                        start()
                    }
        }

    private var searchDebounceTimer: Timer? = null

    init {
        syncDataModule.addListener(
            object : SyncDataListener {
                override fun onSyncDataStateChanged(
                    state: io.github.rygel.outerstellar.platform.sync.engine.module.SyncDataState
                ) {
                    isOnline = state.isOnline
                    messages = state.messages
                    contacts = state.contacts
                    isSyncing = state.isSyncing
                    if (state.syncStatus.isNotBlank()) {
                        status = state.syncStatus
                    }
                    notifyObservers()
                }
            }
        )

        authModule.addListener(
            object : AuthListener {
                override fun onAuthStateChanged(
                    state: io.github.rygel.outerstellar.platform.sync.engine.module.AuthState
                ) {
                    isLoggedIn = state.isLoggedIn
                    userName = state.userName
                    userRole = state.userRole
                    notifyObservers()
                }

                override fun onSessionExpired() {
                    isLoggedIn = false
                    userName = ""
                    userRole = null
                    status = i18nService.translate("swing.session.expired")
                    notifyObservers()
                }
            }
        )

        profileModule.addListener(
            object : ProfileListener {
                override fun onProfileStateChanged(
                    state: io.github.rygel.outerstellar.platform.sync.engine.module.ProfileState
                ) {
                    userEmail = state.userEmail
                    userAvatarUrl = state.userAvatarUrl
                    emailNotificationsEnabled = state.emailNotificationsEnabled
                    pushNotificationsEnabled = state.pushNotificationsEnabled
                    notifyObservers()
                }
            }
        )

        adminModule.addListener(
            object : AdminListener {
                override fun onAdminStateChanged(
                    state: io.github.rygel.outerstellar.platform.sync.engine.module.AdminState
                ) {
                    adminUsers = state.adminUsers
                    notifyObservers()
                }
            }
        )

        notificationModule.addListener(
            object : NotificationListener {
                override fun onNotificationStateChanged(
                    state: io.github.rygel.outerstellar.platform.sync.engine.module.NotificationState
                ) {
                    notifications = state.notifications
                    notifyObservers()
                }
            }
        )
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    private fun notifyObservers() {
        syncFromModules()
        observers.forEach { it() }
    }

    fun refreshTranslations(newI18n: I18nService) {
        this.i18nService = newI18n
        status = i18nService.translate("swing.status.ready")
        loadMessages()
    }

    fun loadMessages() {
        syncDataModule.loadMessages()
    }

    private fun syncFromModules() {
        val sd = syncDataModule.syncDataState
        isOnline = sd.isOnline
        messages = sd.messages
        contacts = sd.contacts
        isSyncing = sd.isSyncing

        val auth = authModule.authState
        isLoggedIn = auth.isLoggedIn
        userName = auth.userName
        userRole = auth.userRole

        adminUsers = adminModule.adminState.adminUsers
        notifications = notificationModule.notificationState.notifications

        val prof = profileModule.profileState
        userEmail = prof.userEmail
        userAvatarUrl = prof.userAvatarUrl
        emailNotificationsEnabled = prof.emailNotificationsEnabled
        pushNotificationsEnabled = prof.pushNotificationsEnabled
    }

    fun createMessage(onValidationError: (String) -> Unit) {
        val result = syncDataModule.createLocalMessage(author, content)
        if (result.isSuccess) {
            content = ""
            status = i18nService.translate("swing.status.created")
            notifyObservers()
        } else {
            val ex = result.exceptionOrNull()
            when (ex) {
                is ValidationException ->
                    onValidationError(ex.message ?: i18nService.translate("swing.validation.messageRequired"))
                is OuterstellarException -> onValidationError(ex.message ?: "Action failed")
                else -> onValidationError(ex?.message ?: "Action failed")
            }
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
        onValidationError: (String) -> Unit,
    ) {
        val result =
            syncDataModule.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
        if (result.isSuccess) {
            status = i18nService.translate("swing.status.created")
        } else {
            onValidationError(result.exceptionOrNull()?.message ?: "Action failed")
        }
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
        onValidationError: (String) -> Unit,
    ) {
        val result =
            syncDataModule.updateContact(syncId, name, emails, phones, socialMedia, company, companyAddress, department)
        if (result.isSuccess) {
            status = i18nService.translate("swing.status.contactUpdated")
            loadMessages()
        } else {
            onValidationError(result.exceptionOrNull()?.message ?: "Action failed")
        }
    }

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.login(user, pass)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = i18nService.translate("swing.status.loggedIn", userName)
                        author = userName
                    }
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun register(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.register(user, pass)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = i18nService.translate("swing.status.registered", userName)
                        author = userName
                    }
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun logout() {
        authModule.logout()
        author = i18nService.translate("swing.author.default")
        status = i18nService.translate("swing.status.loggedOut")
        notifyObservers()
    }

    fun startAutoSync() {
        syncDataModule.startAutoSync()
    }

    fun stopAutoSync() {
        syncDataModule.stopAutoSync()
    }

    fun sync(isAuto: Boolean = false) {
        if (isSyncing) return
        if (!isOnline) {
            status = i18nService.translate("swing.status.offline")
            notifyObservers()
            return
        }

        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = syncDataModule.sync(isAuto)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = syncDataModule.syncDataState.syncStatus
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown error"
                        status = i18nService.translate("swing.status.failed", msg)
                    }
                    notifyObservers()
                }
            }
            .execute()
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.changePassword(currentPassword, newPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadUsers() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.loadUsers()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserEnabled(userId: String, currentEnabled: Boolean) {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.setUserEnabled(userId, !currentEnabled)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserRole(userId: String, currentRole: String) {
        val newRole = if (currentRole == UserRole.ADMIN.name) UserRole.USER.name else UserRole.ADMIN.name
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    adminModule.setUserRole(userId, newRole)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadNotifications() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.loadNotifications()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun markNotificationRead(notificationId: String) {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.markNotificationRead(notificationId)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun markAllNotificationsRead() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    notificationModule.markAllNotificationsRead()
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadProfile(onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    var errorMessage: String? = null
                    val listener =
                        object : SyncDataListener {
                            override fun onSyncError(operation: String, message: String) {
                                if (operation == "loadProfile") errorMessage = message
                            }
                        }
                    syncDataModule.addListener(listener)
                    try {
                        profileModule.loadProfile()
                    } finally {
                        syncDataModule.removeListener(listener)
                    }
                    val loggedIn = authModule.authState.isLoggedIn
                    return if (!loggedIn) false to status else errorMessage?.let { false to it } ?: true to null
                }

                override fun done() {
                    val (success, error) =
                        try {
                            get()
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            false to (e.message ?: "Failed to load profile")
                        }
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun updateProfile(email: String, username: String?, avatarUrl: String?, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = profileModule.updateProfile(email, username, avatarUrl)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun updateNotificationPreferences(
        emailEnabled: Boolean,
        pushEnabled: Boolean,
        onResult: (Boolean, String?) -> Unit,
    ) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> =
                    profileModule.updateNotificationPreferences(emailEnabled, pushEnabled)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun deleteAccount(currentPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = profileModule.deleteAccount(currentPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun requestPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.requestPasswordReset(email)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resetPassword(token: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = authModule.resetPassword(token, newPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        syncDataModule.resolveConflict(syncId, strategy)
        status = i18nService.translate("swing.status.conflictResolved", strategy.name)
        notifyObservers()
    }
}
