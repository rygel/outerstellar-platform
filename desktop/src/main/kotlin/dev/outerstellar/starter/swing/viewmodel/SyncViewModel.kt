package dev.outerstellar.starter.swing.viewmodel

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.OuterstellarException
import dev.outerstellar.starter.model.ValidationException
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.SystemTrayNotifier
import javax.swing.SwingWorker

class SyncViewModel(
    private val messageService: MessageService,
    private val syncService: SyncService,
    private var i18nService: I18nService,
    private val notifier: SystemTrayNotifier? = null
) {
    private val observers = mutableListOf<() -> Unit>()

    var messages: List<MessageSummary> = emptyList()
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
        messages = messageService.listMessages(searchQuery.takeIf { it.isNotBlank() })
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
                } catch (e: Exception) {
                    false to (e.cause?.message ?: e.message ?: "Unknown error")
                }
            }

            override fun done() {
                val (success, error) = get()
                if (success) {
                    status = "Logged in as $userName"
                    author = userName
                }
                onResult(success, error)
                notifyObservers()
            }
        }.execute()
    }

    fun logout() {
        syncService.logout()
        isLoggedIn = false
        userName = ""
        author = i18nService.translate("swing.author.default")
        status = "Logged out"
        notifyObservers()
    }

    fun sync() {
        if (isSyncing) return

        object : SwingWorker<Unit, Unit>() {
            override fun doInBackground() {
                isSyncing = true
                status = i18nService.translate("swing.status.syncing")
                notifyObservers()

                try {
                    val stats = syncService.sync()
                    status = i18nService.translate("swing.status.complete", stats.pushedCount, stats.pulledCount, stats.conflictCount)
                    notifier?.notifySuccess(status)
                } catch (e: Exception) {
                    val errorMsg = i18nService.translate("swing.status.failed", e.cause?.message ?: e.message ?: "unknown error")
                    notifier?.notifyFailure(errorMsg)
                    status = errorMsg
                } finally {
                    isSyncing = false
                    loadMessages()
                }
            }
        }.execute()
    }
}
