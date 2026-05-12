package io.github.rygel.outerstellar.platform.web

class HomePageFactory(private val messageService: io.github.rygel.outerstellar.platform.service.MessageService?) {
    private val messageListComponent = messageService?.let { MessageListComponent(it) }

    private fun requireList() = checkNotNull(messageListComponent) { "MessageService is required for home page" }

    fun buildHomePage(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
    ): Page<HomePage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.nav.home"), "/")
        val messageList = requireList().build(ctx, query, limit, offset, year)

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
                    submitUrl = ctx.url("/messages"),
                    messageList = messageList,
                ),
        )
    }

    fun buildMessageList(
        ctx: WebContext,
        query: String? = null,
        limit: Int = 10,
        offset: Int = 0,
        year: Int? = null,
        isTrash: Boolean = false,
    ): MessageListViewModel = requireList().build(ctx, query, limit, offset, year, isTrash)

    fun buildTrashPage(ctx: WebContext): Page<TrashPage> {
        val i18n = ctx.i18n
        val shell = ctx.shell(i18n.translate("web.trash.title"), "/messages/trash")
        val messageList = buildMessageList(ctx, isTrash = true)

        return Page(
            shell = shell,
            data =
                TrashPage(
                    title = i18n.translate("web.trash.title"),
                    description = i18n.translate("web.trash.description"),
                    messageList = messageList,
                ),
        )
    }
}
