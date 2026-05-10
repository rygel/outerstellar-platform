package io.github.rygel.outerstellar.platform.swing.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactNotFoundException
import io.github.rygel.outerstellar.platform.model.OuterstellarException
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.sync.engine.ConnectivityChecker
import io.github.rygel.outerstellar.platform.sync.engine.DesktopSyncEngine
import io.github.rygel.outerstellar.platform.sync.engine.EngineListener
import io.github.rygel.outerstellar.platform.sync.engine.EngineState
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingWorker

@Suppress("TooManyFunctions")
class SyncViewModel(
    private val engine: DesktopSyncEngine,
    private var i18nService: I18nService,
    private val contactService: ContactService? = null,
) {
    private val observers = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var isOnline: Boolean = engine.state.isOnline
        private set

    @Volatile
    var messages = engine.state.messages
        private set

    @Volatile
    var contacts = engine.state.contacts
        private set

    @Volatile
    var status: String = i18nService.translate("swing.status.ready")
        private set

    @Volatile
    var isSyncing: Boolean = engine.state.isSyncing
        private set

    @Volatile
    var userName: String = engine.state.userName
        private set

    @Volatile
    var isLoggedIn: Boolean = engine.state.isLoggedIn
        private set

    @Volatile
    var userRole: String? = engine.state.userRole
        private set

    @Volatile
    var adminUsers = engine.state.adminUsers
        private set

    @Volatile
    var notifications = engine.state.notifications
        private set

    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }

    @Volatile
    var userEmail: String = engine.state.userEmail
        private set

    @Volatile
    var userAvatarUrl: String? = engine.state.userAvatarUrl
        private set

    @Volatile
    var emailNotificationsEnabled: Boolean = engine.state.emailNotificationsEnabled
        private set

    @Volatile
    var pushNotificationsEnabled: Boolean = engine.state.pushNotificationsEnabled
        private set

    var author: String = i18nService.translate("swing.author.default")
    var content: String = ""
    var searchQuery: String = ""
        set(value) {
            field = value
            loadMessages()
        }

    val connectivityChecker: ConnectivityChecker? = null

    init {
        engine.addListener(
            object : EngineListener {
                override fun onStateChanged(newState: EngineState) {
                    isOnline = newState.isOnline
                    messages = newState.messages
                    contacts = newState.contacts
                    isSyncing = newState.isSyncing
                    userName = newState.userName
                    isLoggedIn = newState.isLoggedIn
                    userRole = newState.userRole
                    adminUsers = newState.adminUsers
                    notifications = newState.notifications
                    userEmail = newState.userEmail
                    userAvatarUrl = newState.userAvatarUrl
                    emailNotificationsEnabled = newState.emailNotificationsEnabled
                    pushNotificationsEnabled = newState.pushNotificationsEnabled
                    if (newState.status.isNotBlank()) {
                        status = newState.status
                    }
                    notifyObservers()
                }

                override fun onSessionExpired() {
                    status = i18nService.translate("swing.session.expired")
                    notifyObservers()
                }
            }
        )
    }

    fun addObserver(observer: () -> Unit) {
        observers.add(observer)
    }

    private fun notifyObservers() {
        observers.forEach { it() }
    }

    fun refreshTranslations(newI18n: I18nService) {
        this.i18nService = newI18n
        status = i18nService.translate("swing.status.ready")
        loadMessages()
    }

    fun loadMessages() {
        engine.loadMessages()
    }

    fun createMessage(onValidationError: (String) -> Unit) {
        val result = engine.createLocalMessage(author, content)
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
        val result = engine.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
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
        try {
            val svc = contactService ?: throw ContactNotFoundException(syncId)
            val stored = svc.getContactBySyncId(syncId) ?: throw ContactNotFoundException(syncId)
            val updated =
                stored.copy(
                    name = name,
                    emails = emails,
                    phones = phones,
                    socialMedia = socialMedia,
                    company = company,
                    companyAddress = companyAddress,
                    department = department,
                    dirty = true,
                )
            svc.updateContact(updated)
            status = i18nService.translate("swing.status.contactUpdated")
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            onValidationError(e.message ?: "Action failed")
        }
    }

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = engine.login(user, pass)

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
                override fun doInBackground(): Result<Unit> = engine.register(user, pass)

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
        engine.logout()
        author = i18nService.translate("swing.author.default")
        status = i18nService.translate("swing.status.loggedOut")
        notifyObservers()
    }

    fun startAutoSync() {
        engine.startAutoSync()
    }

    fun stopAutoSync() {
        engine.stopAutoSync()
    }

    fun sync(isAuto: Boolean = false) {
        if (isSyncing) return
        if (!isOnline) {
            status = i18nService.translate("swing.status.offline")
            notifyObservers()
            return
        }

        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = engine.sync(isAuto)

                override fun done() {
                    val result = get()
                    if (result.isSuccess) {
                        status = engine.state.status
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
                override fun doInBackground(): Result<Unit> = engine.changePassword(currentPassword, newPassword)

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
                    engine.loadUsers()
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
                    engine.setUserEnabled(userId, !currentEnabled)
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserRole(userId: String, currentRole: String) {
        val newRole = if (currentRole == "ADMIN") "USER" else "ADMIN"
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    engine.setUserRole(userId, newRole)
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
                    engine.loadNotifications()
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
                    engine.markNotificationRead(notificationId)
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
                    engine.markAllNotificationsRead()
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
                        object : EngineListener {
                            override fun onStateChanged(newState: EngineState) {}

                            override fun onError(operation: String, message: String) {
                                if (operation == "loadProfile") errorMessage = message
                            }
                        }
                    engine.addListener(listener)
                    try {
                        engine.loadProfile()
                    } finally {
                        engine.removeListener(listener)
                    }
                    val loggedIn = engine.state.isLoggedIn
                    return if (!loggedIn) false to engine.state.status
                    else errorMessage?.let { false to it } ?: true to null
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
                override fun doInBackground(): Result<Unit> = engine.updateProfile(email, username, avatarUrl)

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
                    engine.updateNotificationPreferences(emailEnabled, pushEnabled)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Result<Unit>, Unit>() {
                override fun doInBackground(): Result<Unit> = engine.deleteAccount()

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
                override fun doInBackground(): Result<Unit> = engine.requestPasswordReset(email)

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
                override fun doInBackground(): Result<Unit> = engine.resetPassword(token, newPassword)

                override fun done() {
                    val result = get()
                    onResult(result.isSuccess, result.exceptionOrNull()?.message)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        engine.resolveConflict(syncId, strategy)
        status = i18nService.translate("swing.status.conflictResolved", strategy.name)
        notifyObservers()
    }
}
