package io.github.rygel.outerstellar.platform.web

class InfraPageFactory(private val repository: io.github.rygel.outerstellar.platform.persistence.MessageRepository?) {

    fun buildFooterStatus(ctx: WebContext): FooterStatusFragment {
        val i18n = ctx.i18n
        val msgCount = repository?.countMessages() ?: 0L
        val dirtyCount = repository?.countDirtyMessages() ?: 0L
        return FooterStatusFragment(text = i18n.translate("web.footer.status", msgCount, dirtyCount))
    }

    fun buildConflictResolveModal(ctx: WebContext, syncId: String): ConflictResolveViewModel {
        val message =
            checkNotNull(repository) { "MessageRepository is required for conflict resolution" }.findBySyncId(syncId)
                ?: throw io.github.rygel.outerstellar.platform.model.MessageNotFoundException(syncId)
        val serverVersion =
            org.http4k.format.KotlinxSerialization.asA(
                message.syncConflict!!,
                io.github.rygel.outerstellar.platform.sync.SyncMessage::class,
            )

        val i18n = ctx.i18n
        return ConflictResolveViewModel(
            syncId = syncId,
            myAuthor = message.author,
            myContent = message.content,
            serverAuthor = serverVersion.author,
            serverContent = serverVersion.content,
            resolveUrl = ctx.url("/messages/resolve/$syncId"),
            modalTitle = i18n.translate("web.conflict.title"),
            myVersionLabel = i18n.translate("web.conflict.my.version"),
            serverVersionLabel = i18n.translate("web.conflict.server.version"),
            keepMineLabel = i18n.translate("web.conflict.keep.mine"),
            acceptServerLabel = i18n.translate("web.conflict.accept.server"),
            decideLaterLabel = i18n.translate("web.conflict.decide.later"),
            description = i18n.translate("web.conflict.description"),
            authorLabel = i18n.translate("web.conflict.author"),
        )
    }
}
