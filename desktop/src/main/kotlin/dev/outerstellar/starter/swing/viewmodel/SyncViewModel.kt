package dev.outerstellar.starter.swing.viewmodel

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import dev.outerstellar.starter.swing.SystemTrayNotifier
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.viewmodel.SyncViewModel")

class SyncViewModel(
    private val messageService: MessageService,
    private val syncService: SyncService,
    private val i18nService: I18nService,
    private val notifier: SystemTrayNotifier? = null
) {
    private val observers = CopyOnWriteArrayList<() -> Unit>()

    var messages: List<MessageSummary> = emptyList()
        private set

    var status: String = i18nService.translate("swing.status.ready")
        private set

    var isSyncing: Boolean = false
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

    fun removeObserver(observer: () -> Unit) {
        observers.remove(observer)
    }

    private fun notifyObservers() {
        SwingUtilities.invokeLater {
            observers.forEach { it() }
        }
    }

    fun loadMessages(initialStatus: String? = null) {
        messages = if (searchQuery.isBlank()) {
            messageService.listMessages()
        } else {
            messageService.listMessages(query = searchQuery)
        }
        val dirtyCount = messageService.listDirtyMessages().size
        val currentStatus = initialStatus ?: status
        status = i18nService.translate("swing.status.summary", currentStatus, messages.size, dirtyCount)
        notifyObservers()
    }

    fun createMessage(onValidationError: (String) -> Unit) {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) {
            onValidationError(i18nService.translate("swing.validation.messageRequired"))
            return
        }

        messageService.createLocalMessage(author.ifBlank { i18nService.translate("swing.author.default") }, trimmedContent)
        content = ""
        loadMessages(i18nService.translate("swing.status.created"))
    }

    fun sync() {
        if (isSyncing) return

        isSyncing = true
        status = i18nService.translate("swing.status.syncing")
        notifyObservers()

        object : SwingWorker<String, Unit>() {
            override fun doInBackground(): String {
                val stats = syncService.sync()
                return i18nService.translate(
                    "swing.status.complete",
                    stats.pushedCount,
                    stats.pulledCount,
                    stats.conflictCount
                )
            }

            override fun done() {
                isSyncing = false
                try {
                    val result = get()
                    notifier?.notifySuccess(result)
                    loadMessages(result)
                } catch (e: Exception) {
                    logger.error("Sync failed", e)
                    val errorMsg = i18nService.translate("swing.status.failed", e.cause?.message ?: e.message ?: "unknown error")
                    notifier?.notifyFailure(errorMsg)
                    status = errorMsg
                    loadMessages()
                }
            }
        }.execute()
    }
}
