package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val emptyMessage: String,
    val deleteUrl: String,
    val restoreUrl: String? = null,
    val refreshUrl: String,
    val pagination: PaginationViewModel? = null,
    val isTrash: Boolean = false,
) : ViewModel {
    override fun template(): String = "dev/outerstellar/starter/web/components/MessageList"
}

private const val DEFAULT_PAGE_SIZE = 10
private const val ARG_QUERY = 0
private const val ARG_LIMIT = 1
private const val ARG_OFFSET = 2
private const val ARG_YEAR = 3
private const val ARG_IS_TRASH = 4

class MessageListComponent(private val repository: MessageRepository) :
    WebComponent<MessageListViewModel> {

    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val query = args.getOrNull(ARG_QUERY) as? String
        val limit = args.getOrNull(ARG_LIMIT) as? Int ?: DEFAULT_PAGE_SIZE
        val offset = args.getOrNull(ARG_OFFSET) as? Int ?: 0
        val year = args.getOrNull(ARG_YEAR) as? Int
        val isTrash = args.getOrNull(ARG_IS_TRASH) as? Boolean ?: false

        val i18n = ctx.i18n

        val items = repository.listMessages(query, year, limit, offset, includeDeleted = isTrash)
        val total = repository.countMessages(query, year, includeDeleted = isTrash)

        return buildViewModel(ctx, items, total, limit, offset, query, year, isTrash, i18n)
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
        i18n: com.outerstellar.i18n.I18nService,
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
                    (if (query != null) "&query=$query" else "") +
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
                    "No deleted messages found."
                } else {
                    i18n.translate("web.home.list.empty")
                },
            deleteUrl = ctx.url("/messages"),
            restoreUrl = ctx.url("/messages/restore"),
            refreshUrl = currentUrl,
            pagination = pagination,
            isTrash = isTrash,
        )
    }
}
