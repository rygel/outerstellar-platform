package io.github.rygel.outerstellar.platform.web

class ContactsPageFactory(private val contactService: io.github.rygel.outerstellar.platform.service.ContactService?) {

    fun buildContactsPage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 12,
        offset: Int = 0,
    ): Page<ContactsPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.contacts"), "/contacts")

        val dbContacts = contactService?.listContacts(query, limit, offset) ?: emptyList()
        val totalCount = contactService?.countContacts(query) ?: 0L
        val currentPage = (offset / limit) + 1
        val hasPrevious = offset > 0
        val hasNext = offset + limit < totalCount
        val previousUrl = ctx.url("/contacts?limit=$limit&offset=${maxOf(0, offset - limit)}")
        val nextUrl = ctx.url("/contacts?limit=$limit&offset=${offset + limit}")

        return Page(
            shell = shell,
            data =
                ContactsPage(
                    title = "Contacts Directory",
                    description = "A list of all your contacts.",
                    contacts =
                        dbContacts.map {
                            ContactViewModel(
                                syncId = it.syncId,
                                name = it.name,
                                emails = it.emails,
                                phones = it.phones,
                                socialMedia = it.socialMedia,
                                company = it.company,
                                companyAddress = it.companyAddress,
                                department = it.department,
                                deleteUrl = ctx.url("/contacts/${it.syncId}/delete"),
                                editUrl = ctx.url("/contacts/${it.syncId}/edit"),
                            )
                        },
                    currentPage = currentPage,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    previousUrl = previousUrl,
                    nextUrl = nextUrl,
                    totalCount = totalCount,
                    createLabel = i18n.translate("web.contacts.create"),
                    editTitle = i18n.translate("web.contacts.edit"),
                    deleteTitle = i18n.translate("web.contacts.delete"),
                    deleteConfirmLabel = i18n.translate("web.contacts.delete.confirm"),
                    contactsTotalLabel = i18n.translate("web.contacts.total"),
                    previousPageTitle = i18n.translate("web.contacts.previous.page"),
                    nextPageTitle = i18n.translate("web.contacts.next.page"),
                ),
        )
    }

    fun buildContactForm(ctx: WebContext, syncId: String? = null): ContactFormFragment {
        val i18n = ctx.i18n
        val existing = syncId?.let { contactService?.getContactBySyncId(it) }
        return ContactFormFragment(
            syncId = existing?.syncId ?: "",
            name = existing?.name ?: "",
            emails = existing?.emails?.joinToString(", ") ?: "",
            phones = existing?.phones?.joinToString(", ") ?: "",
            socialMedia = existing?.socialMedia?.joinToString(", ") ?: "",
            company = existing?.company ?: "",
            companyAddress = existing?.companyAddress ?: "",
            department = existing?.department ?: "",
            submitUrl = ctx.url(if (syncId != null) "/contacts/$syncId/update" else "/contacts"),
            isEdit = syncId != null,
            titleLabel =
                if (syncId != null) {
                    i18n.translate("web.contacts.edit")
                } else {
                    i18n.translate("web.contacts.create")
                },
            nameLabel = i18n.translate("web.contacts.form.name"),
            emailsLabel = i18n.translate("web.contacts.form.emails"),
            phonesLabel = i18n.translate("web.contacts.form.phones"),
            socialLabel = i18n.translate("web.contacts.form.social"),
            companyLabel = i18n.translate("web.contacts.form.company"),
            addressLabel = i18n.translate("web.contacts.form.address"),
            departmentLabel = i18n.translate("web.contacts.form.department"),
            saveLabel = i18n.translate("web.contacts.form.save"),
            cancelLabel = i18n.translate("web.contacts.form.cancel"),
        )
    }
}
