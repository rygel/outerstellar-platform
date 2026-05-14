package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.MessageService
import java.time.Instant

class MessageExportProvider(private val messageService: MessageService?) : ExportProvider {
    override val entityType: String = "message"
    override val displayName: String = "Messages"

    private val messages
        get() = messageService?.listMessages(limit = 1000)?.items ?: emptyList()

    override fun headers(): List<String> = listOf("Author", "Content", "Updated", "Dirty")

    override fun exportCsv(): String = buildString {
        appendLine(CsvUtils.toCsvRow(headers()))
        messages.forEach { msg ->
            val ts = Instant.ofEpochMilli(msg.updatedAtEpochMs).toString().take(10)
            appendLine(CsvUtils.toCsvRow(listOf(msg.author, msg.content, ts, msg.dirty.toString())))
        }
    }

    override fun exportJson(): String =
        messages.joinToString(",\n", "[\n", "\n]") { msg ->
            """  {"author":"${msg.author}","content":"${msg.content}","updated":${msg.updatedAtEpochMs},"dirty":${msg.dirty}}"""
        }
}
