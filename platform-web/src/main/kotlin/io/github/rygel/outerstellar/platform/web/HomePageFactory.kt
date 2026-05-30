package io.github.rygel.outerstellar.platform.web

class HomePageFactory(
    private val messageService: io.github.rygel.outerstellar.platform.service.MessageService?,
    private val contactTrashListFactory: ContactTrashListFactory? = null,
) {
    private val messageListComponent = messageService?.let { MessageListComponent(it) }

    private fun requireList() = checkNotNull(messageListComponent) { "MessageService is required for home page" }

    fun buildHomePage(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
    ): Page<HomePage> {
        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.nav.home"), "/")
        val messageList = requireList().build(ctx, shellRenderer, query, limit, offset, year)

        return Page(
            shell = shell,
            data =
                HomePage(
                    eyebrow = i18n.translate("web.home.eyebrow"),
                    intro = i18n.translate("web.home.intro"),
                    features =
                        listOf(
                            HomeFeature(
                                i18n.translate("web.feature.http.label"),
                                i18n.translate("web.feature.http.value"),
                            ),
                            HomeFeature(i18n.translate("web.feature.db.label"), i18n.translate("web.feature.db.value")),
                            HomeFeature(
                                i18n.translate("web.feature.sync.label"),
                                i18n.translate("web.feature.sync.value"),
                            ),
                            HomeFeature(
                                i18n.translate("web.feature.desktop.label"),
                                i18n.translate("web.feature.desktop.value"),
                            ),
                        ),
                    composerTitle = i18n.translate("web.home.composer.title"),
                    composerIntro = i18n.translate("web.home.composer.intro"),
                    authorPlaceholder = i18n.translate("web.home.composer.author"),
                    contentPlaceholder = i18n.translate("web.home.composer.content"),
                    submitLabel = i18n.translate("web.home.composer.submit"),
                    submitUrl = shellRenderer.url("/messages"),
                    messageList = messageList,
                ),
        )
    }

    fun buildMessageList(
        ctx: RequestContext,
        shellRenderer: ShellRenderer,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
        isTrash: Boolean = false,
    ): MessageListViewModel = requireList().build(ctx, shellRenderer, query, limit, offset, year, isTrash)

    fun buildMessageEditForm(shellRenderer: ShellRenderer, syncId: String): MessageEditFormFragment {
        val msg =
            messageService?.findBySyncId(syncId)
                ?: throw io.github.rygel.outerstellar.platform.model.MessageNotFoundException(syncId)
        val i18n = shellRenderer.i18n
        return MessageEditFormFragment(
            syncId = msg.syncId,
            author = msg.author,
            content = msg.content,
            submitUrl = shellRenderer.url("/messages/$syncId/update"),
            titleLabel = i18n.translate("web.messages.edit"),
            authorLabel = i18n.translate("web.home.composer.author"),
            contentLabel = i18n.translate("web.home.composer.content"),
            saveLabel = i18n.translate("web.messages.save"),
            cancelLabel = i18n.translate("web.messages.cancel"),
        )
    }

    fun buildTrashPage(ctx: RequestContext, shellRenderer: ShellRenderer): Page<TrashPage> {
        val i18n = shellRenderer.i18n
        val shell = shellRenderer.shell(i18n.translate("web.trash.title"), "/messages/trash")
        val messageList = buildMessageList(ctx, shellRenderer, isTrash = true)
        val contactList = contactTrashListFactory?.build(shellRenderer)

        return Page(
            shell = shell,
            data =
                TrashPage(
                    title = i18n.translate("web.trash.title"),
                    description = i18n.translate("web.trash.description"),
                    messageList = messageList,
                    contactList = contactList,
                ),
        )
    }
}
