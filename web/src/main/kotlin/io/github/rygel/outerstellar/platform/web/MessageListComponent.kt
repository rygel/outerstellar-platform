package io.github.rygel.outerstellar.platform.web

import io.github.rygel.outerstellar.platform.model.MessageSummary
import io.github.rygel.outerstellar.platform.model.PaginationMetadata
import io.github.rygel.outerstellar.platform.service.MessageService
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val emptyMessage: String,
    val deleteUrl: String,
    val restoreUrl: String? = null,
    val refreshUrl: String,
    val pagination: PaginationViewModel? = null,
    val isTrash: Boolean = false,
    val restoreTitle: String = "Restore message",
    val editTitle: String = "Edit message (Local only)",
    val deleteTitle: String = "Delete message",
    val deleteConfirm: String = "Are you sure you want to delete this message?",
    val showingPageLabel: String = "Showing page",
    val ofLabel: String = "of",
    val localBadge: String = "Local",
    val conflictBadge: String = "Conflict",
) : ViewModel {
    override fun template(): String = "dev/outerstellar/platform/web/components/MessageList"
}

private const val DEFAULT_PAGE_SIZE = 10
private const val ARG_QUERY = 0
private const val ARG_LIMIT = 1
private const val ARG_OFFSET = 2
private const val ARG_YEAR = 3
private const val ARG_IS_TRASH = 4

class MessageListComponent(private val messageService: MessageService) :
    WebComponent<MessageListViewModel> {

    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val query = args.getOrNull(ARG_QUERY) as? String
        val limit = args.getOrNull(ARG_LIMIT) as? Int ?: DEFAULT_PAGE_SIZE
        val offset = args.getOrNull(ARG_OFFSET) as? Int ?: 0
        val year = args.getOrNull(ARG_YEAR) as? Int
        val isTrash = args.getOrNull(ARG_IS_TRASH) as? Boolean ?: false

        val i18n = ctx.i18n

        val result =
            if (isTrash) {
                messageService.listDeletedMessages(query, year, limit, offset)
            } else {
                messageService.listMessages(query, year, limit, offset)
            }

        return buildViewModel(
            ctx,
            result.items,
            result.metadata.totalItems,
            limit,
            offset,
            query,
            year,
            isTrash,
            i18n,
        )
    }

    @Suppress("LongParameterList")
    private fun buildViewModel(
        ctx: WebContext,
        items: List<MessageSummary>,
        total: Long,
        limit: Int,
        offset: Int,
        query: String?,
        year: Int?,
        isTrash: Boolean,
        i18n: io.github.rygel.outerstellar.i18n.I18nService,
    ): MessageListViewModel {
        val metadata =
            PaginationMetadata(
                currentPage = (offset / limit) + 1,
                pageSize = limit,
                totalItems = total,
            )

        fun createUrl(page: Int): String {
            val newOffset = (page - 1) * limit
            val baseUrl = if (isTrash) "/messages/trash" else "/"
            return ctx.url(
                "$baseUrl?limit=$limit&offset=$newOffset" +
                    (if (query != null) "&q=$query" else "") +
                    (if (year != null) "&year=$year" else "")
            )
        }

        val pagination =
            if (metadata.totalPages > 1) {
                PaginationViewModel(
                    currentPage = metadata.currentPage,
                    totalPages = metadata.totalPages,
                    hasPrevious = metadata.hasPrevious,
                    hasNext = metadata.hasNext,
                    previousUrl = metadata.previousPage?.let { createUrl(it) },
                    nextUrl = metadata.nextPage?.let { createUrl(it) },
                    pages =
                        (1..metadata.totalPages).map { p ->
                            PageNumberViewModel(p, createUrl(p), p == metadata.currentPage)
                        },
                )
            } else {
                null
            }

        val currentUrl = createUrl(metadata.currentPage)

        return MessageListViewModel(
            messages = items,
            emptyMessage =
                if (isTrash) {
                    i18n.translate("web.trash.empty")
                } else {
                    i18n.translate("web.home.list.empty")
                },
            deleteUrl = ctx.url("/messages"),
            restoreUrl = ctx.url("/messages/restore"),
            refreshUrl = currentUrl,
            pagination = pagination,
            isTrash = isTrash,
            restoreTitle = i18n.translate("web.messages.restore"),
            editTitle = i18n.translate("web.messages.edit"),
            deleteTitle = i18n.translate("web.messages.delete"),
            deleteConfirm = i18n.translate("web.messages.delete.confirm"),
            showingPageLabel = i18n.translate("web.messages.showing.page"),
            ofLabel = i18n.translate("web.messages.of"),
            localBadge = i18n.translate("web.messages.local.badge"),
            conflictBadge = i18n.translate("web.messages.conflict.badge"),
        )
    }
}
