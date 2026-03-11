package dev.outerstellar.starter.swing.viewmodel

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.ContactSummary
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.OuterstellarException
import dev.outerstellar.starter.model.SyncException
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.service.ContactService
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.swing.SystemTrayNotifier
import dev.outerstellar.starter.sync.SyncService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.SwingWorker

@Suppress("TooManyFunctions")
class SyncViewModel(
    private val messageService: MessageService,
    private val contactService: ContactService? = null,
    private val syncService: SyncService,
    private var i18nService: I18nService,
    private val notifier: SystemTrayNotifier? = null
) {
    private val observers = mutableListOf<() -> Unit>()
    private var autoSyncExecutor: ScheduledExecutorService? = null

    var messages: List<MessageSummary> = emptyList()
        private set

    var contacts: List<ContactSummary> = emptyList()
        private set

    var status: String = i18nService.translate("swing.status.ready")
        private set

    var isSyncing: Boolean = false
        private set

    var userName: String = ""
        private set

    var isLoggedIn: Boolean = false
        private set

    var author: String = i18nService.translate("swing.author.default")
    var content: String = ""
    var searchQuery: String = ""
        set(value) {
            field = value
            loadMessages()
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

    fun login(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
            override fun doInBackground(): Pair<Boolean, String?> {
                return try {
                    val result = syncService.login(user, pass)
                    userName = result.username
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
                    status = i18nService.translate("swing.status.loggedIn", userName)
                    author = userName
                    startAutoSync()
                }
                onResult(success, error)
                notifyObservers()
            }
        }.execute()
    }

    fun register(user: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        object : SwingWorker<Pair<Boolean, String?>, Unit>() {
            override fun doInBackground(): Pair<Boolean, String?> {
                return try {
                    val result = syncService.register(user, pass)
                    userName = result.username
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
        }.execute()
    }

    fun logout() {
        stopAutoSync()
        syncService.logout()
        isLoggedIn = false
        userName = ""
        author = i18nService.translate("swing.author.default")
        status = i18nService.translate("swing.status.loggedOut")
        notifyObservers()
    }

    fun startAutoSync() {
        if (autoSyncExecutor != null) return

        autoSyncExecutor = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleAtFixedRate({
                if (!isSyncing && isLoggedIn) {
                    sync(isAuto = true)
                }
            }, 1, 1, TimeUnit.MINUTES)
        }
    }

    fun stopAutoSync() {
        autoSyncExecutor?.shutdown()
        autoSyncExecutor = null
    }

    fun sync(isAuto: Boolean = false) {
        if (isSyncing) return

        object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                isSyncing = true
                status = if (isAuto) "Auto-syncing..." else i18nService.translate("swing.status.syncing")
                notifyObservers()

                try {
                    val stats = syncService.sync()
                    status = i18nService.translate(
                        "swing.status.complete",
                        stats.pushedCount,
                        stats.pulledCount,
                        stats.conflictCount
                    )
                    if (!isAuto) {
                        notifier?.notifySuccess(status)
                    }
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    val errorMsg = i18nService.translate(
                        "swing.status.failed",
                        e.cause?.message ?: e.message ?: "unknown error"
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
        }.execute()
    }

    fun resolveConflict(syncId: String, strategy: String) {
        try {
            messageService.resolveConflict(syncId, strategy)
            status = "Conflict resolved using $strategy strategy"
            loadMessages()
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            status = "Failed to resolve conflict: ${e.message}"
            notifyObservers()
        }
    }
}
