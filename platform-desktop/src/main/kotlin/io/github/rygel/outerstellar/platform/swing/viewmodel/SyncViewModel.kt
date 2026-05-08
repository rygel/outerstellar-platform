package io.github.rygel.outerstellar.platform.swing.viewmodel

import io.github.rygel.outerstellar.i18n.I18nService
import io.github.rygel.outerstellar.platform.analytics.AnalyticsService
import io.github.rygel.outerstellar.platform.analytics.NoOpAnalyticsService
import io.github.rygel.outerstellar.platform.model.ConflictStrategy
import io.github.rygel.outerstellar.platform.model.ContactNotFoundException
import io.github.rygel.outerstellar.platform.model.ContactSummary
import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.OuterstellarException
import io.github.rygel.outerstellar.platform.model.SessionExpiredException
import io.github.rygel.outerstellar.platform.model.SyncException
import io.github.rygel.outerstellar.platform.model.UserSummary
import io.github.rygel.outerstellar.platform.model.ValidationException
import io.github.rygel.outerstellar.platform.service.ContactService
import io.github.rygel.outerstellar.platform.service.MessageService
import io.github.rygel.outerstellar.platform.swing.ConnectivityChecker
import io.github.rygel.outerstellar.platform.swing.SystemTrayNotifier
import io.github.rygel.outerstellar.platform.sync.SyncService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.SwingWorker
import org.slf4j.LoggerFactory

@Suppress("TooManyFunctions")
class SyncViewModel(
    private val messageService: MessageService,
    private val contactService: ContactService? = null,
    private val syncService: SyncService,
    private var i18nService: I18nService,
    private val notifier: SystemTrayNotifier? = null,
    private val analytics: AnalyticsService = NoOpAnalyticsService(),
    val connectivityChecker: ConnectivityChecker? = null,
) {
    private val logger = LoggerFactory.getLogger(SyncViewModel::class.java)

    private val observers = CopyOnWriteArrayList<() -> Unit>()
    private var autoSyncExecutor: ScheduledExecutorService? = null

    @Volatile
    var isOnline: Boolean = connectivityChecker?.isOnline ?: true
        private set

    @Volatile
    var messages: List<MessageSummary> = emptyList()
        private set

    @Volatile
    var contacts: List<ContactSummary> = emptyList()
        private set

    @Volatile
    var status: String = i18nService.translate("swing.status.ready")
        private set

    @Volatile
    var isSyncing: Boolean = false
        private set

    @Volatile
    var userName: String = ""
        private set

    @Volatile
    var isLoggedIn: Boolean = false
        private set

    @Volatile
    var userRole: String? = null
        private set

    @Volatile
    var adminUsers: List<UserSummary> = emptyList()
        private set

    @Volatile
    var notifications: List<io.github.rygel.outerstellar.platform.model.NotificationSummary> = emptyList()
        private set

    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }

    @Volatile
    var userEmail: String = ""
        private set

    @Volatile
    var userAvatarUrl: String? = null
        private set

    @Volatile
    var emailNotificationsEnabled: Boolean = true
        private set

    @Volatile
    var pushNotificationsEnabled: Boolean = true
        private set

    var author: String = i18nService.translate("swing.author.default")
    var content: String = ""
    var searchQuery: String = ""
        set(value) {
            field = value
            loadMessages()
        }

    init {
        connectivityChecker?.addObserver { online ->
            isOnline = online
            notifyObservers()
        }
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
        messages = messageService.listMessages(searchQuery.takeIf { it.isNotBlank() }).items
        contacts = contactService?.listContacts(searchQuery.takeIf { it.isNotBlank() }) ?: emptyList()
        notifyObservers()
    }

    fun createMessage(onValidationError: (String) -> Unit) {
        try {
            messageService.createLocalMessage(author, content)
            content = ""
            status = i18nService.translate("swing.status.created")
            loadMessages()
        } catch (e: ValidationException) {
            onValidationError(e.message ?: i18nService.translate("swing.validation.messageRequired"))
        } catch (e: OuterstellarException) {
            onValidationError(e.message ?: "Action failed")
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
        try {
            contactService?.createContact(name, emails, phones, socialMedia, company, companyAddress, department)
            status = i18nService.translate("swing.status.created")
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            onValidationError(e.message ?: "Action failed")
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
            val stored = contactService?.getContactBySyncId(syncId) ?: throw ContactNotFoundException(syncId)
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
            contactService.updateContact(updated)
            status = i18nService.translate("swing.status.contactUpdated")
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            onValidationError(e.message ?: "Action failed")
        }
    }

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        val result = syncService.login(user, pass)
                        userName = result.username
                        userRole = result.role
                        isLoggedIn = true
                        analytics.identify(userName, mapOf("role" to (userRole ?: "user"), "platform" to "desktop"))
                        analytics.track(userName, "User Logged In", mapOf("platform" to "desktop"))
                        true to null
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.cause?.message ?: e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    if (success) {
                        status = i18nService.translate("swing.status.loggedIn", userName)
                        author = userName
                        startAutoSync()
                    }
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun register(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        val result = syncService.register(user, pass)
                        userName = result.username
                        userRole = result.role
                        isLoggedIn = true
                        true to null
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.cause?.message ?: e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    if (success) {
                        status = i18nService.translate("swing.status.registered", userName)
                        author = userName
                        startAutoSync()
                    }
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun logout() {
        stopAutoSync()
        syncService.logout()
        isLoggedIn = false
        userRole = null
        userName = ""
        userEmail = ""
        userAvatarUrl = null
        adminUsers = emptyList()
        author = i18nService.translate("swing.author.default")
        status = i18nService.translate("swing.status.loggedOut")
        notifyObservers()
    }

    fun startAutoSync() {
        if (autoSyncExecutor != null) return

        autoSyncExecutor =
            Executors.newSingleThreadScheduledExecutor().apply {
                scheduleAtFixedRate(
                    {
                        if (!isSyncing && isLoggedIn) {
                            sync(isAuto = true)
                        }
                    },
                    1,
                    1,
                    TimeUnit.MINUTES,
                )
            }
    }

    fun stopAutoSync() {
        autoSyncExecutor?.shutdownNow()
        autoSyncExecutor = null
    }

    fun sync(isAuto: Boolean = false) {
        if (isSyncing) return
        if (!isOnline) {
            status = i18nService.translate("swing.status.offline")
            notifyObservers()
            return
        }

        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    isSyncing = true
                    status =
                        if (isAuto) i18nService.translate("swing.status.autoSyncing")
                        else i18nService.translate("swing.status.syncing")
                    notifyObservers()

                    try {
                        val stats = syncService.sync()
                        status =
                            i18nService.translate(
                                "swing.status.complete",
                                stats.pushedCount,
                                stats.pulledCount,
                                stats.conflictCount,
                            )
                        analytics.track(
                            userName,
                            "Sync Completed",
                            mapOf(
                                "pushed" to stats.pushedCount,
                                "pulled" to stats.pulledCount,
                                "conflicts" to stats.conflictCount,
                                "platform" to "desktop",
                            ),
                        )
                        if (!isAuto) {
                            notifier?.notifySuccess(status)
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        val errorMsg =
                            i18nService.translate(
                                "swing.status.failed",
                                e.cause?.message ?: e.message ?: "unknown error",
                            )
                        if (!isAuto) {
                            notifier?.notifyFailure(errorMsg)
                        }
                        status = errorMsg
                    } finally {
                        isSyncing = false
                        loadMessages()
                    }
                }
            }
            .execute()
    }

    fun changePassword(currentPassword: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.changePassword(currentPassword, newPassword)
                        true to null
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                        false to i18nService.translate("swing.session.expired")
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun loadUsers() {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    try {
                        adminUsers = syncService.listUsers()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        status = e.message ?: "Failed to load users"
                    }
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
                    try {
                        syncService.setUserEnabled(userId, !currentEnabled)
                        adminUsers = syncService.listUsers()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        status = e.message ?: "Failed to toggle user"
                    }
                }

                override fun done() {
                    notifyObservers()
                }
            }
            .execute()
    }

    fun toggleUserRole(userId: String, currentRole: String) {
        object : SwingWorker<Unit, Unit>() {
                override fun doInBackground() {
                    try {
                        val newRole = if (currentRole == "ADMIN") "USER" else "ADMIN"
                        syncService.setUserRole(userId, newRole)
                        adminUsers = syncService.listUsers()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        status = e.message ?: "Failed to toggle role"
                    }
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
                    try {
                        notifications = syncService.listNotifications()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        logger.debug("Failed to load notifications", e)
                    }
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
                    try {
                        syncService.markNotificationRead(notificationId)
                        notifications = syncService.listNotifications()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        status = e.message ?: "Failed to mark notification as read"
                    }
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
                    try {
                        syncService.markAllNotificationsRead()
                        notifications = syncService.listNotifications()
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        status = e.message ?: "Failed to mark all notifications as read"
                    }
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
                    return try {
                        val profile = syncService.fetchProfile()
                        userEmail = profile.email
                        userAvatarUrl = profile.avatarUrl
                        emailNotificationsEnabled = profile.emailNotificationsEnabled
                        pushNotificationsEnabled = profile.pushNotificationsEnabled
                        true to null
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                        false to i18nService.translate("swing.session.expired")
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Failed to load profile")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun updateProfile(email: String, username: String?, avatarUrl: String?, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.updateProfile(email, username, avatarUrl)
                        // Reload to sync local state
                        val profile = syncService.fetchProfile()
                        userName = profile.username
                        userEmail = profile.email
                        userAvatarUrl = profile.avatarUrl
                        true to null
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                        false to i18nService.translate("swing.session.expired")
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
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
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.updateNotificationPreferences(emailEnabled, pushEnabled)
                        emailNotificationsEnabled = emailEnabled
                        pushNotificationsEnabled = pushEnabled
                        true to null
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired", e)
                        handleSessionExpired()
                        false to i18nService.translate("swing.session.expired")
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun deleteAccount(onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.deleteAccount()
                        true to null
                    } catch (e: SessionExpiredException) {
                        logger.debug("Session expired during account deletion", e)
                        false to i18nService.translate("swing.session.expired")
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    if (success) {
                        // Force local session clear after account deletion
                        isLoggedIn = false
                        userRole = null
                        userName = ""
                        userEmail = ""
                        stopAutoSync()
                    }
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    private fun handleSessionExpired() {
        isLoggedIn = false
        userRole = null
        userName = ""
        userEmail = ""
        userAvatarUrl = null
        stopAutoSync()
        status = i18nService.translate("swing.session.expired")
    }

    fun requestPasswordReset(email: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.requestPasswordReset(email)
                        true to null
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resetPassword(token: String, newPassword: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
                override fun doInBackground(): Pair<Boolean, String?> {
                    return try {
                        syncService.resetPassword(token, newPassword)
                        true to null
                    } catch (e: SyncException) {
                        false to e.message
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        false to (e.message ?: "Unknown error")
                    }
                }

                override fun done() {
                    val (success, error) = get()
                    onResult(success, error)
                    notifyObservers()
                }
            }
            .execute()
    }

    fun resolveConflict(syncId: String, strategy: ConflictStrategy) {
        try {
            messageService.resolveConflict(syncId, strategy)
            status = i18nService.translate("swing.status.conflictResolved", strategy.name)
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            status = i18nService.translate("swing.status.conflictFailed", e.message ?: "unknown")
            notifyObservers()
        }
    }
}
