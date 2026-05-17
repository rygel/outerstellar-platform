package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.MessageService
import java.time.Instant

class MessageExportProvider(private val messageService: MessageService?) : ExportProvider {
    override val entityType: String = "message"
    override val displayName: String = "Messages"

    override fun headers(): List<String> = listOf("Author", "Content", "Updated", "Dirty")

    override fun exportCsv(): String = buildString {
        appendLine(CsvUtils.toCsvRow(headers()))
        if (messageService == null) return@buildString
        var offset = 0
        val pageSize = 500
        do {
            val page = messageService.listMessages(limit = pageSize, offset = offset)
            page.items.forEach { msg ->
                val ts = Instant.ofEpochMilli(msg.updatedAtEpochMs).toString().take(10)
                appendLine(CsvUtils.toCsvRow(listOf(msg.author, msg.content, ts, msg.dirty.toString())))
            }
            offset += pageSize
        } while (page.items.size == pageSize)
    }

    override fun exportJson(): String = buildString {
        appendLine("[")
        if (messageService == null) {
            appendLine("]")
            return@buildString
        }
        val allItems = mutableListOf<String>()
        var offset = 0
        val pageSize = 500
        do {
            val page = messageService.listMessages(limit = pageSize, offset = offset)
            page.items.forEach { msg ->
                allItems.add(
                    """  {"author":"${msg.author}","content":"${msg.content}","updated":${msg.updatedAtEpochMs},"dirty":${msg.dirty}}"""
                )
            }
            offset += pageSize
        } while (page.items.size == pageSize)
        appendLine(allItems.joinToString(",\n"))
        appendLine("]")
    }
}
