package dev.outerstellar.starter.swing.viewmodel

import com.outerstellar.i18n.I18nService
import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.service.MessageService
import dev.outerstellar.starter.sync.SyncService
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("dev.outerstellar.starter.swing.viewmodel.SyncViewModel")

class SyncViewModel(
    private val messageService: MessageService,
    private val syncService: SyncService,
    private val i18nService: I18nService
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
        messages = messageService.listMessages()
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
                    loadMessages(get())
                } catch (e: Exception) {
                    logger.error("Sync failed", e)
                    status = i18nService.translate("swing.status.failed", e.cause?.message ?: e.message ?: "unknown error")
                    loadMessages()
                }
            }
        }.execute()
    }
}
