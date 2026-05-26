package io.github.rygel.outerstellar.platform.export

import io.github.rygel.outerstellar.platform.service.ContactService
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

    override fun exportJson(): String {
        if (contactService == null) return "[]"
        val allItems = mutableListOf<ContactExportRow>()
        var offset = 0
        val pageSize = 500
        do {
            val page = contactService.listContacts(limit = pageSize, offset = offset)
            page.forEach { c ->
                allItems.add(
                    ContactExportRow(name = c.name, emails = c.emails, company = c.company, department = c.department)
                )
            }
            offset += pageSize
        } while (page.size == pageSize)
        return Json.encodeToString(allItems)
    }

    @Serializable
    data class ContactExportRow(val name: String, val emails: List<String>, val company: String, val department: String)
}
