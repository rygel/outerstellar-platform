package dev.outerstellar.starter.web

import dev.outerstellar.starter.model.MessageSummary
import dev.outerstellar.starter.model.PaginationMetadata
import dev.outerstellar.starter.persistence.MessageRepository
import org.http4k.template.ViewModel

data class MessageListViewModel(
    val messages: List<MessageSummary>,
    val emptyMessage: String,
    val deleteUrl: String,
    val pagination: PaginationViewModel? = null
) : ViewModel

class MessageListComponent(private val repository: MessageRepository) : WebComponent<MessageListViewModel> {
    
    override fun build(ctx: WebContext, vararg args: Any?): MessageListViewModel {
        val query = args.getOrNull(0) as? String
        val limit = args.getOrNull(1) as? Int ?: 10
        val offset = args.getOrNull(2) as? Int ?: 0
        val year = args.getOrNull(3) as? Int
        
        val i18n = ctx.i18n
        val items = repository.listMessages(query, year, limit, offset)
        val total = repository.countMessages(query, year)
        
        val metadata = PaginationMetadata(
            currentPage = (offset / limit) + 1,
            pageSize = limit,
            totalItems = total
        )

        fun createUrl(page: Int): String {
            val newOffset = (page - 1) * limit
            return ctx.url("/?limit=$limit&offset=$newOffset" + 
                (if (query != null) "&query=$query" else "") + 
                (if (year != null) "&year=$year" else ""))
        }

        val pagination = if (metadata.totalPages > 1) {
            PaginationViewModel(
                currentPage = metadata.currentPage,
                totalPages = metadata.totalPages,
                hasPrevious = metadata.hasPrevious,
                hasNext = metadata.hasNext,
                previousUrl = metadata.previousPage?.let { createUrl(it) },
                nextUrl = metadata.nextPage?.let { createUrl(it) },
                pages = (1..metadata.totalPages).map { p ->
                    PageNumberViewModel(p, createUrl(p), p == metadata.currentPage)
                }
            )
        } else null

        return MessageListViewModel(
            messages = items,
            emptyMessage = i18n.translate("web.home.list.empty"),
            deleteUrl = ctx.url("/messages/delete"),
            pagination = pagination
        )
    }
}
