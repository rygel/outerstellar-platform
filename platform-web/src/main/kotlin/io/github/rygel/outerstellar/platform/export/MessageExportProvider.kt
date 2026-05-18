package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.MessageService
import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    override fun exportJson(): String {
        if (messageService == null) return "[]"
        val allItems = mutableListOf<MessageExportRow>()
        var offset = 0
        val pageSize = 500
        do {
            val page = messageService.listMessages(limit = pageSize, offset = offset)
            page.items.forEach { msg ->
                allItems.add(
                    MessageExportRow(
                        author = msg.author,
                        content = msg.content,
                        updated = msg.updatedAtEpochMs,
                        dirty = msg.dirty,
                    )
                )
            }
            offset += pageSize
        } while (page.items.size == pageSize)
        return Json.encodeToString(allItems)
    }

    @Serializable
    data class MessageExportRow(val author: String, val content: String, val updated: Long, val dirty: Boolean)
}
