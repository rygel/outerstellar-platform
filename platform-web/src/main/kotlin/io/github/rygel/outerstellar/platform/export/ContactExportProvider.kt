package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.ContactService

class ContactExportProvider(private val contactService: ContactService?) : ExportProvider {
    override val entityType: String = "contact"
    override val displayName: String = "Contacts"

    override fun headers(): List<String> = listOf("Name", "Emails", "Phones", "Company", "Department")

    override fun exportCsv(): String = buildString {
        appendLine(CsvUtils.toCsvRow(headers()))
        if (contactService == null) return@buildString
        var offset = 0
        val pageSize = 500
        do {
            val page = contactService.listContacts(limit = pageSize, offset = offset)
            page.forEach { c ->
                appendLine(
                    CsvUtils.toCsvRow(
                        listOf(
                            c.name,
                            c.emails.joinToString("; "),
                            c.phones.joinToString("; "),
                            c.company,
                            c.department,
                        )
                    )
                )
            }
            offset += pageSize
        } while (page.size == pageSize)
    }

    override fun exportJson(): String = buildString {
        appendLine("[")
        if (contactService == null) {
            appendLine("]")
            return@buildString
        }
        val allItems = mutableListOf<String>()
        var offset = 0
        val pageSize = 500
        do {
            val page = contactService.listContacts(limit = pageSize, offset = offset)
            page.forEach { c ->
                allItems.add(
                    """  {"name":"${c.name}","emails":["${c.emails.joinToString("\",\"")}"],"company":"${c.company}","department":"${c.department}"}"""
                )
            }
            offset += pageSize
        } while (page.size == pageSize)
        appendLine(allItems.joinToString(",\n"))
        appendLine("]")
    }
}
