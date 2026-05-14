package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.ContactService

class ContactExportProvider(private val contactService: ContactService?) : ExportProvider {
    override val entityType: String = "contact"
    override val displayName: String = "Contacts"

    private val contacts
        get() = contactService?.listContacts(limit = 1000) ?: emptyList()

    override fun headers(): List<String> = listOf("Name", "Emails", "Phones", "Company", "Department")

    override fun exportCsv(): String = buildString {
        appendLine(CsvUtils.toCsvRow(headers()))
        contacts.forEach { c ->
            appendLine(
                CsvUtils.toCsvRow(
                    listOf(c.name, c.emails.joinToString("; "), c.phones.joinToString("; "), c.company, c.department)
                )
            )
        }
    }

    override fun exportJson(): String =
        contacts.joinToString(",\n", "[\n", "\n]") { c ->
            """  {"name":"${c.name}","emails":["${c.emails.joinToString("\",\"")}"],"company":"${c.company}","department":"${c.department}"}"""
        }
}
