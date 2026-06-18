package io.github.rygel.outerstellar.platform.web

class ContactTrashListFactory(
    private val contactService: io.github.rygel.outerstellar.platform.service.ContactService
) {
    fun build(shellRenderer: ShellRenderer): ContactTrashListViewModel {
        val i18n = shellRenderer.i18n
        val dbContacts = contactService.listContacts(limit = 100, offset = 0, includeDeleted = true)
        return ContactTrashListViewModel(
            contacts =
                dbContacts.map {
                    ContactTrashItemViewModel(
                        syncId = it.syncId,
                        name = it.name,
                        emails = it.emails,
                        phones = it.phones,
                        company = it.company,
                        department = it.department,
                        restoreUrl = shellRenderer.url("/contacts/${it.syncId}/restore"),
                    )
                },
            emptyMessage = i18n.translate("web.trash.contacts.empty"),
            refreshUrl = shellRenderer.url("/contacts/trash/list"),
            title = i18n.translate("web.trash.contacts"),
            restoreTitle = i18n.translate("web.contacts.restore"),
        )
    }
}
